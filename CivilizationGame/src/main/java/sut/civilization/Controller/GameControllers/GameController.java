package sut.civilization.Controller.GameControllers;

import sut.civilization.Controller.Controller;
import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;

public class GameController extends Controller {

    protected static CivilizedUnit selectedCivilizedUnit;
    protected static User currentTurnUser;

    protected LandController landController = new LandController();

    // ── Selectors ─────────────────────────────────────────────────────────────

    public static CivilizedUnit getSelectedCivilizedUnit() { return selectedCivilizedUnit; }
    public static void setSelectedCivilizedUnit(CivilizedUnit u) { selectedCivilizedUnit = u; }

    public static User getCurrentTurnUser() { return currentTurnUser; }
    public static void setCurrentTurnUser(User u) { currentTurnUser = u; }

    // ── Nation selection ──────────────────────────────────────────────────────
    // old hook from when we picked a NationType at game start. nation object already
    // exists by now so this basically does nothing anymore (NationType doesn't give bonuses
    // in the 4-resource version we ended up with)
    public void chooseNation(int nationIndex, int playerIndex) {
        if (playerIndex >= Game.instance.getPlayersInGame().size()) return;
        User player = Game.instance.getPlayersInGame().get(playerIndex);
        if (player.getNation() == null) player.setNation(new sut.civilization.Model.Classes.Nation());
    }

    // ── Unit / map queries ────────────────────────────────────────────────────

    public String selectCivilizedUnit(Matcher matcher) {
        int i = Integer.parseInt(matcher.group("x"));
        int j = Integer.parseInt(matcher.group("y"));
        if (Game.instance.map[i][j].getCivilizedUnit() == null)
            return "There is no civilized unit here!";
        if (!Game.instance.map[i][j].getCivilizedUnit().getOwnerNation().equals(currentTurnUser.getNation()))
            return "You can't select opponent's unit";
        selectedCivilizedUnit = Game.instance.map[i][j].getCivilizedUnit();
        return selectedCivilizedUnit.getName() + " is now selected";
    }

    // ── Turn readiness ────────────────────────────────────────────────────────
    // if units still have AP left we just warn the player, we don't block ending turn.
    // the actual warning popup is in GamePlayController.nextTurn(), this here just
    // checks if we need to show it

    public static boolean hasIdleUnits() {
        Nation n = currentTurnUser.getNation();
        for (Unit u : n.getUnits()) {
            if (u.getUnitStatus() != UnitStatus.AWAKE || u.getCurrentAp() <= 0) continue;
            if (u instanceof CivilizedUnit && ((CivilizedUnit) u).isStationed()) continue;
            return true;
        }
        return false;
    }

    // ── End turn (single-player) ──────────────────────────────────────────────

    public static String endTurn() {
        selectedCivilizedUnit = null;
        nextGameTurn();
        return "next turn: " + Game.instance.getTurn();
    }

    // ── NEXT GAME TURN (steps 10, 11, 12 from the spec) ──────────────────────
    // order matters here: AP refresh -> building production + townhall queue (same step)
    // -> upkeep (uses balance after production) -> starvation check -> border expander
    // cleanup -> fog of war update

    public static void nextGameTurn() {
        Game.instance.setTurn(Game.instance.getTurn() + 1);
        System.out.println("DEBUG: nextGameTurn() called, turn is now " + Game.instance.getTurn());

        for (User user : Game.instance.getPlayersInGame()) {
            Nation nation = user.getNation();

            // step 10a: refresh AP for all units
            for (Unit unit : nation.getUnits()) {
                unit.refreshAp();
            }

            // step 10b: reset net rates before we recalc them below
            nation.getFood().resetNetRate();
            nation.getWood().resetNetRate();
            nation.getStone().resetNetRate();
            nation.getIron().resetNetRate();

            // step 10c: townhall trickle (small wood+food safeguard so you don't get stuck)
            if (nation.getTownHall() != null) {
                nation.getTownHall().produceSafeguardTrickle();
            }

            // step 10d: buildings produce resources (workers -> resources)
            // this also does step 11 (upkeep + 3 turn destroy) inside it
            processBuildings(nation);

            // townhall production queue moves forward here too, same step as building
            // production (not at the very end like i first had it)
            processTownHallQueue(nation);

            // step 10g: each unit eats 1 food per turn
            for (Unit unit : nation.getUnits()) {
                nation.getFood().add(-1);
                nation.getFood().addToNetRate(-1);
            }

            // step 10f: starving if net food rate is negative AND balance already below 0
            boolean starving = nation.getFood().getNetRate() < 0
                    && nation.getFood().getBalance() < 0;
            nation.setInStarvation(starving);
            System.out.println("DEBUG turn=" + Game.instance.getTurn() + " nation food balance="
                    + nation.getFood().getBalance() + " starving=" + starving);

            // population growth stops while starving
            if (nation.getTownHall() != null) {
                nation.getTownHall().tickPopulationGrowth(nation.getFood().getNetRate());
            }

            // step 12: border expander gets consumed once it expands the border
            processBorderExpanders(nation);
        }

        // fog of war update for everyone
        updateFogOfWar();

        LandController.updateDistances();
    }

    // ── step 11: building upkeep + destroy after 3 missed turns ───────────────

    private static void processBuildings(Nation nation) {
        // go through every tile this nation owns and process its improvement (if any)
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            Improvement imp = tile.getImprovement();
            if (imp == null) continue;
            // can't use resources on tiles we haven't discovered yet
            if (tile.getVisibility() == 0) continue;

            ImprovementType type = imp.getImprovementType();

            // iron mine uses its own separate iron deposit, everything else pulls from
            // the tile's normal resource (wood/stone/food)
            Resource deposit = (type == ImprovementType.IRON_MINE) ? tile.getIronResource() : tile.getResource();

            // produce from stationed workers, capped by how much deposit is left
            int wanted = imp.calcBaseProduction();
            // tech_tools gives 1.5x on stone/iron mines
            if (wanted > 0 && type.producedResource != null
                    && (type == ImprovementType.STONE_MINE || type == ImprovementType.IRON_MINE)
                    && nation.hasTechnology(TechnologyType.TECH_TOOLS)) {
                wanted = (int) (wanted * 1.5);
            }
            int produced = imp.extractProduction(wanted, deposit);
            if (produced > 0 && type.producedResource != null) {
                // add straight to balance (not just netRate) so upkeep below sees the
                // updated amount - production has to land before upkeep gets deducted
                nation.getResource(type.producedResource).add(produced);
                nation.getResource(type.producedResource).addToNetRate(produced);
            }
            // deposit ran out -> building can never produce again so just destroy it and
            // free up the tile (e.g. depleted stone mine shouldn't block building an iron
            // mine on the same mountain)
            boolean depleted = deposit != null && deposit.isDepleted();
            if (wanted > 0 && depleted) {
                for (CivilizedUnit worker : new ArrayList<>(imp.getStationedWorkers())) {
                    imp.removeWorker(worker);
                    worker.leaveStation();
                }
                tile.setImprovement(null);
                continue;
            }

            // building with no workers isn't costing us anything, only charge upkeep if
            // it's actually staffed. otherwise builders placing improvements before we have
            // enough workers would silently drain wood for nothing
            if (imp.getWorkerCount() == 0) {
                imp.resetMissedUpkeep();
                continue;
            }

            // pay upkeep against the balance we already updated above
            boolean paid = nation.getResource(type.upkeepResource).spend(type.upkeepAmount);
            if (paid) {
                nation.getResource(type.upkeepResource).addToNetRate(-type.upkeepAmount);
            }
            if (!paid) {
                imp.recordMissedUpkeep();
                if (imp.shouldBeDestroyed()) {
                    // kick out the workers before we destroy the building
                    for (CivilizedUnit worker : new ArrayList<>(imp.getStationedWorkers())) {
                        worker.leaveStation();
                    }
                    tile.setImprovement(null);
                }
            } else {
                imp.resetMissedUpkeep();
            }
        }
    }

    // ── step 12: border expander gets removed once it's used its ability ─────

    private static void processBorderExpanders(Nation nation) {
        Iterator<Unit> it = nation.getUnits().iterator();
        while (it.hasNext()) {
            Unit unit = it.next();
            if (!(unit instanceof CivilizedUnit)) continue;
            CivilizedUnit cu = (CivilizedUnit) unit;
            if (cu.getCivilizedUnitType() != CivilizedUnitType.BORDER_EXPANDER) continue;
            if (cu.hasExpanded()) {
                // remove it from the tile
                Pair<Integer, Integer> loc = cu.getLocation();
                if (Pair.isValid(loc)) {
                    Game.instance.map[loc.x][loc.y].setCivilizedUnit(null);
                }
                it.remove();
            }
        }
    }

    // ── TownHall queue advancement ────────────────────────────────────────────

    private static void processTownHallQueue(Nation nation) {
        TownHall townHall = nation.getTownHall();
        if (townHall == null || !townHall.hasQueuedProduction()) return;

        CivilizedUnitType trained = townHall.advanceQueue();
        if (trained == null) return;

        // hit unit cap - don't just throw away the finished unit (already paid for it),
        // keep it queued and try again next turn
        if (!nation.canTrainUnit()) { townHall.requeueFront(trained); return; }

        Land mainLand = townHall.getMainLand();
        if (mainLand == null) { townHall.requeueFront(trained); return; }

        // try the town hall tile first. if something's already parked there (happens a lot
        // since that's where units get sent back to) look for a free neighboring tile we
        // own instead of just blocking the queue
        Land spawnTile = null;
        if (mainLand.getCivilizedUnit() == null) {
            spawnTile = mainLand;
        } else {
            Pair<Integer, Integer> thCoord = new Pair<>(mainLand.getI(), mainLand.getJ());
            for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(thCoord)) {
                if (!Pair.isValid(nb)) continue;
                if (!nation.ownsHex(nb.x, nb.y)) continue;
                Land candidate = Game.instance.map[nb.x][nb.y];
                if (candidate.getCivilizedUnit() == null) { spawnTile = candidate; break; }
            }
        }
        if (spawnTile == null) { townHall.requeueFront(trained); return; }

        CivilizedUnit newUnit = new CivilizedUnit(trained, nation,
                new Pair<>(spawnTile.getI(), spawnTile.getJ()));
        spawnTile.setCivilizedUnit(newUnit);
        nation.addUnit(newUnit);
    }

    // ── fog of war update ──────────────────────────────────────────────────────

    private static void updateFogOfWar() {
        // grab nations for all active players
        ArrayList<sut.civilization.Model.Classes.Nation> activeNations = new ArrayList<>();
        for (User user : Game.instance.getPlayersInGame()) {
            activeNations.add(user.getNation());
        }

        // tiles that were fully visible drop down to "shadow" (seen before, not fog), never
        // back to full black - explorer's permanent reveal is tracked via seerNations
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                if (tile.getVisibility() == 2) {
                    // was fully visible, drop to shadow not fog
                    tile.setVisibility(1);
                } else if (tile.getVisibility() == 0) {
                    // still fog - check if we've explored it before
                    for (sut.civilization.Model.Classes.Nation n : activeNations) {
                        if (tile.getSeerNations().contains(n)) {
                            tile.setVisibility(1); // stays shadow forever once explored
                            break;
                        }
                    }
                }
            }
        }

        for (User user : Game.instance.getPlayersInGame()) {
            for (Unit unit : user.getNation().getUnits()) {
                int visionRadius = 1;
                boolean isExplorer = false;
                if (unit instanceof CivilizedUnit) {
                    visionRadius = ((CivilizedUnit) unit).getCivilizedUnitType().visionRadius;
                    isExplorer = ((CivilizedUnit) unit).getCivilizedUnitType() == CivilizedUnitType.EXPLORER;
                }

                revealAround(unit.getLocation(), visionRadius, user.getNation(), isExplorer);
            }
        }
    }

    // BFS reveal helper, also used by LandController.updateLandVisibility() so both places
    // agree on how vision radius turns into revealed tiles. isExplorer flags the tiles as
    // "explored by an Explorer" for this nation - border expander needs hexes an Explorer
    // actually saw, not just any unit passing by
    static void revealAround(Pair<Integer, Integer> center, int radius,
                                     sut.civilization.Model.Classes.Nation nation, boolean isExplorer) {
        // BFS out to radius, marking tiles visible + remembering this nation saw them
        ArrayList<Pair<Integer, Integer>> visited = new ArrayList<>();
        ArrayList<Pair<Integer, Integer>> frontier = new ArrayList<>();
        frontier.add(center);
        visited.add(center);

        for (int r = 0; r <= radius; r++) {
            ArrayList<Pair<Integer, Integer>> next = new ArrayList<>();
            for (Pair<Integer, Integer> hex : frontier) {
                if (Pair.isValid(hex)) {
                    Land tile = Game.instance.map[hex.x][hex.y];
                    tile.setVisibility(2);
                    tile.addSeerNation(nation); // remember that this nation has seen this tile
                    if (isExplorer) tile.addExplorerSeenNation(nation);
                }
                if (r < radius) {
                    for (int k = 0; k < 6; k++) {
                        Pair<Integer, Integer> neighbor = LandController.getNeighborIndex(hex, k);
                        if (Pair.isValid(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            next.add(neighbor);
                        }
                    }
                }
            }
            frontier = next;
        }
    }
}

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
    // Legacy game-start hook: assigns NationType to player. Nation object is
    // already created; this is a no-op in the new 4-resource model since
    // NationType is cosmetic only (no bonuses).
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
    // Doc: idle units with remaining AP must trigger a *warning* before ending the
    // turn, not an outright block - the player can still choose to end turn anyway.
    // The warning/confirmation itself lives in GamePlayController.nextTurn(); this
    // method only performs the actual turn advance once the player has confirmed.

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

    // ── NEXT GAME TURN (Steps 10, 11, 12) ────────────────────────────────────
    // Order: AP refresh → building production + TownHall queue (same step) →
    //        upkeep deduct (against post-production balance) → starvation check
    //        → BorderExpander cleanup → fog-of-war update

    public static void nextGameTurn() {
        Game.instance.setTurn(Game.instance.getTurn() + 1);

        for (User user : Game.instance.getPlayersInGame()) {
            Nation nation = user.getNation();

            // ── Step 10a: Refresh AP for all units ───────────────────────────
            for (Unit unit : nation.getUnits()) {
                unit.refreshAp();
            }

            // ── Step 10b: Reset net rates before recalculating ───────────────
            nation.getFood().resetNetRate();
            nation.getWood().resetNetRate();
            nation.getStone().resetNetRate();
            nation.getIron().resetNetRate();

            // ── Step 10c: TownHall safeguard trickle (wood + food) ────────────
            if (nation.getTownHall() != null) {
                nation.getTownHall().produceSafeguardTrickle();
            }

            // ── Step 10d: Building production (Workers → resources) ──────────
            //    Also handle Step 11: upkeep deduction + 3-turn destruction
            processBuildings(nation);

            // ── Step 10d (cont'd): TownHall production queue advances alongside
            //    building production, per spec's turn order (same step, not the last thing).
            processTownHallQueue(nation);

            // ── Step 10g: Unit food consumption ───────────────────────────────
            // Each living unit consumes 1 food/turn. Applied directly to balance (food is
            // allowed to float below 0, see Currency.floorAtZero) so the starvation check
            // below sees the true post-consumption balance, and reflected in netRate for HUD.
            for (Unit unit : nation.getUnits()) {
                nation.getFood().add(-1);
                nation.getFood().addToNetRate(-1);
            }

            // ── Step 10f: Starvation check ────────────────────────────────────
            // Starvation: net food rate is negative AND food storage is below 0.
            boolean starving = nation.getFood().getNetRate() < 0
                    && nation.getFood().getBalance() < 0;
            nation.setInStarvation(starving);

            // ── Population growth (frozen while starving) ─────────────────────
            if (nation.getTownHall() != null) {
                nation.getTownHall().tickPopulationGrowth(nation.getFood().getNetRate());
            }

            // ── Step 12: BorderExpander consume mechanic ──────────────────────
            processBorderExpanders(nation);
        }

        // ── Fog-of-war update ─────────────────────────────────────────────────
        updateFogOfWar();

        LandController.updateDistances();
    }

    // ── Step 11: building upkeep + 3-turn destruction ────────────────────────

    private static void processBuildings(Nation nation) {
        // Collect all improvements from every tile owned by this nation
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            Improvement imp = tile.getImprovement();
            if (imp == null) continue;
            // Doc: resources on undiscovered hexes can't be used.
            if (tile.getVisibility() == 0) continue;

            ImprovementType type = imp.getImprovementType();

            // Iron Mine draws from the tile's independent iron deposit; every other
            // extractive building draws from the tile's primary resource (wood/stone/food).
            Resource deposit = (type == ImprovementType.IRON_MINE) ? tile.getIronResource() : tile.getResource();

            // Produce resources from stationed workers, capped by the tile's remaining deposit
            int wanted = imp.calcBaseProduction();
            // TECH_TOOLS: 1.5× multiplier on stone/iron mines
            if (wanted > 0 && type.producedResource != null
                    && (type == ImprovementType.STONE_MINE || type == ImprovementType.IRON_MINE)
                    && nation.hasTechnology(TechnologyType.TECH_TOOLS)) {
                wanted = (int) (wanted * 1.5);
            }
            int produced = imp.extractProduction(wanted, deposit);
            if (produced > 0 && type.producedResource != null) {
                // Credit production to the balance immediately (not just netRate) so
                // upkeep below is checked against the up-to-date balance, per spec's
                // turn order: production must land before upkeep is deducted.
                nation.getResource(type.producedResource).add(produced);
                nation.getResource(type.producedResource).addToNetRate(produced);
            }
            // Deposit exhausted: the improvement can never produce again, so destroy it
            // immediately and free the hex (e.g. a depleted Stone Mine no longer blocks
            // building an Iron Mine on the same mountain tile).
            boolean depleted = deposit != null && deposit.isDepleted();
            if (wanted > 0 && depleted) {
                for (CivilizedUnit worker : new ArrayList<>(imp.getStationedWorkers())) {
                    imp.removeWorker(worker);
                    worker.leaveStation();
                }
                tile.setImprovement(null);
                continue;
            }

            // Pay upkeep (against the balance already updated with this turn's production above)
            boolean paid = nation.getResource(type.upkeepResource).spend(type.upkeepAmount);
            if (paid) {
                nation.getResource(type.upkeepResource).addToNetRate(-type.upkeepAmount);
            }
            if (!paid) {
                imp.recordMissedUpkeep();
                if (imp.shouldBeDestroyed()) {
                    // Evict all stationed workers before destroying
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

    // ── Step 12: BorderExpander consume mechanic ──────────────────────────────
    // A BorderExpander that has used its expand ability is removed from the map.

    private static void processBorderExpanders(Nation nation) {
        Iterator<Unit> it = nation.getUnits().iterator();
        while (it.hasNext()) {
            Unit unit = it.next();
            if (!(unit instanceof CivilizedUnit)) continue;
            CivilizedUnit cu = (CivilizedUnit) unit;
            if (cu.getCivilizedUnitType() != CivilizedUnitType.BORDER_EXPANDER) continue;
            if (cu.hasExpanded()) {
                // Remove from map tile
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

        // Unit cap hit - don't discard the finished unit (its cost was already paid), hold it
        // and retry next turn instead.
        if (!nation.canTrainUnit()) { townHall.requeueFront(trained); return; }

        Land mainLand = townHall.getMainLand();
        if (mainLand == null) { townHall.requeueFront(trained); return; }

        // Prefer the Town Hall tile itself; if a unit is already parked there (very common -
        // it's the tile players return units to), fall back to any free neighboring tile in
        // the player's own territory rather than blocking the queue forever.
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

    // ── Fog-of-war update ─────────────────────────────────────────────────────

    private static void updateFogOfWar() {
        // Collect all nations that have active players
        ArrayList<sut.civilization.Model.Classes.Nation> activeNations = new ArrayList<>();
        for (User user : Game.instance.getPlayersInGame()) {
            activeNations.add(user.getNation());
        }

        // Reset visible tiles: tiles previously seen by any active nation become shadow (1),
        // never fully black (0) — Explorer permanent reveal is preserved via seerNations.
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                if (tile.getVisibility() == 2) {
                    // Was visible — drop to shadow, not fog
                    tile.setVisibility(1);
                } else if (tile.getVisibility() == 0) {
                    // Fog tile: check if any active nation has explored it before
                    for (sut.civilization.Model.Classes.Nation n : activeNations) {
                        if (tile.getSeerNations().contains(n)) {
                            tile.setVisibility(1); // permanently shadow once explored
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

    /**
     * BFS reveal helper shared with LandController.updateLandVisibility() so there is
     * a single source of truth for how vision radius translates into revealed tiles.
     * {@code isExplorer} marks the discovered hexes as "explored by an Explorer" for this
     * nation — required by Border Expander, per spec, which only accepts hexes an Explorer
     * has actually revealed (not just any unit's passing visibility).
     */
    static void revealAround(Pair<Integer, Integer> center, int radius,
                                     sut.civilization.Model.Classes.Nation nation, boolean isExplorer) {
        // BFS out to radius — marks tiles visible and records this nation as an explorer
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

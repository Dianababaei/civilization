package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.ImprovementType;

import java.util.ArrayList;

/**
 * Phase 2 disasters (P2-54/55). Only the earthquake exists so far - the 5% roll,
 * the damage helpers and the "tell the UI to animate it" plumbing are written so
 * later disasters (flood, bear...) can reuse them.
 */
public class DisasterController extends GameController {

    public static final double DISASTER_CHANCE_PER_TURN = 0.05;
    public static final int EARTHQUAKE_RADIUS = 2;        // 19 hexes
    public static final int EARTHQUAKE_UNIT_DAMAGE = 10;
    public static final int EARTHQUAKE_TH_DAMAGE = 50;

    // set whenever a quake happens so the UI can shake/flash the area after End Turn.
    // the UI reads and clears these; headless drivers just ignore them
    public static ArrayList<Pair<Integer, Integer>> lastQuakeArea = null;
    public static Pair<Integer, Integer> lastQuakeCenter = null;

    /** Headless test drivers turn this off so random quakes can't break their scenes. */
    public static boolean randomDisastersEnabled = true;

    /** Called once at the start of every game turn: 5% chance something terrible happens. */
    public static void rollForDisaster() {
        if (!randomDisastersEnabled) return;
        // rolls come from the game's saved RNG (P2-66), so loading a save can't
        // reroll a different disaster outcome than the original playthrough
        java.util.Random rng = Game.instance.getRng();
        if (rng.nextDouble() >= DISASTER_CHANCE_PER_TURN) return;
        int i = rng.nextInt(Consts.MAP_SIZE.amount.x);
        int j = rng.nextInt(Consts.MAP_SIZE.amount.y);
        triggerEarthquake(new Pair<>(i, j));
    }

    /** All hexes within `radius` steps of center (simple BFS ring growth). */
    public static ArrayList<Pair<Integer, Integer>> hexesInRadius(Pair<Integer, Integer> center, int radius) {
        ArrayList<Pair<Integer, Integer>> area = new ArrayList<>();
        area.add(center);
        for (int step = 0; step < radius; step++) {
            ArrayList<Pair<Integer, Integer>> grow = new ArrayList<>();
            for (Pair<Integer, Integer> hex : area)
                for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(hex))
                    if (Pair.isValid(nb) && !area.contains(nb) && !grow.contains(nb)) grow.add(nb);
            area.addAll(grow);
        }
        return area;
    }

    /**
     * Earthquake (P2-55): radius 2 around the center. Units take 10 damage (military
     * units with 1-2 HP die outright - P2-08 says HP<=0 removes the unit immediately),
     * buildings and roads collapse (infrastructure is the main target), a Town Hall in
     * the area takes 50 damage but never drops below 1 HP.
     */
    public static String triggerEarthquake(Pair<Integer, Integer> center) {
        ArrayList<Pair<Integer, Integer>> area = hexesInRadius(center, EARTHQUAKE_RADIUS);
        int unitsHit = 0, unitsKilled = 0, buildingsDown = 0, roadsDown = 0;
        boolean townHallHit = false;

        for (Pair<Integer, Integer> hex : area) {
            Land tile = Game.instance.map[hex.x][hex.y];

            CivilizedUnit unit = tile.getCivilizedUnit();
            if (unit != null) {
                unitsHit++;
                unit.setHp(unit.getHp() - EARTHQUAKE_UNIT_DAMAGE);
                if (unit.getHp() <= 0) {
                    removeDeadUnit(unit);
                    unitsKilled++;
                }
            }

            // infrastructure is the quake's main target. Trading Posts are neutral
            // pre-placed structures and stay standing (they anchor the trade tests)
            Improvement imp = tile.getImprovement();
            if (imp != null && imp.getImprovementType() != ImprovementType.TRADING_POST) {
                for (CivilizedUnit worker : new ArrayList<>(imp.getStationedWorkers())) {
                    imp.removeWorker(worker);
                    worker.leaveStation();
                }
                if (imp.getImprovementType() == ImprovementType.TOWN) {
                    for (User u : Game.instance.getPlayersInGame())
                        if (u.getNation().ownsHex(hex.x, hex.y))
                            u.getNation().removeTownUnitCapBonus();
                }
                tile.setImprovement(null);
                buildingsDown++;
            }
            if (tile.hasRoad()) {
                tile.setHasRoad(false);
                roadsDown++;
            }

            // Town Hall: -50 HP but NEVER below 1 (per spec it can't die to a quake)
            for (User u : Game.instance.getPlayersInGame()) {
                TownHall th = u.getNation().getTownHall();
                if (th != null && th.getMainLand() == tile) {
                    th.setHp(Math.max(1, th.getHp() - EARTHQUAKE_TH_DAMAGE));
                    townHallHit = true;
                }
            }
        }

        // destroyed roads change movement costs - refresh the cached path matrix
        if (roadsDown > 0) {
            LandController.initializeDistances();
            LandController.updateDistances();
        }

        lastQuakeArea = area;
        lastQuakeCenter = center;

        String summary = "EARTHQUAKE at (" + center.x + "," + center.y + ")! "
                + unitsHit + " unit(s) hit (" + unitsKilled + " killed), "
                + (buildingsDown + roadsDown) + " building(s)/road(s) destroyed"
                + (townHallHit ? ", Town Hall damaged -" + EARTHQUAKE_TH_DAMAGE + " HP" : "")
                + ". Area: " + area.size() + " hexes"
                + (area.size() < 19 ? " (radius 2 clipped by the map edge)" : "") + ".";
        notifyTurn(summary);
        return summary;
    }

    // ── Bear attacks (P2-57) ──────────────────────────────────────────────────
    // A forest hex reachable from the Town Hall is the bear DEN. When player units
    // come within 3 hexes of it a bear (HP 120, AP 2) comes out - two bears if 3+
    // units crowd the area (max 2). Bears hunt the NEAREST CIVILIAN first, then
    // military, never damage buildings, and go home (despawn) when no targets are
    // left near the den - then the area stays calm for at least 5 turns.

    public static final int BEAR_HOME_RADIUS = 3;
    public static final int BEAR_CALM_TURNS = 5;
    public static final int BEAR_CROWD_UNITS = 3;
    public static final int BEAR_MAX = 2;

    /** Headless drivers turn this off so roaming bears can't disturb their scenes. */
    public static boolean bearDenEnabled = true;

    /** Picks the den: the first forest hex 3+ hexes from the TH, reachable on foot. */
    public static void ensureBearDen() {
        if (Game.instance.bearDen != null || !bearDenEnabled) return;
        TownHall th = null;
        for (User u : Game.instance.getPlayersInGame())
            if (u.getNation().getTownHall() != null) th = u.getNation().getTownHall();
        if (th == null) return;
        Pair<Integer, Integer> thCoord = new Pair<>(th.getMainLand().getI(), th.getMainLand().getJ());

        // walk outward ring by ring so the den lands close enough to actually matter
        ArrayList<Pair<Integer, Integer>> tooClose = hexesInRadius(thCoord, 2);
        for (int radius = 3; radius <= 8; radius++) {
            for (Pair<Integer, Integer> hex : hexesInRadius(thCoord, radius)) {
                if (!Pair.isValid(hex) || tooClose.contains(hex)) continue;
                Land tile = Game.instance.map[hex.x][hex.y];
                if (tile.getLandType() == sut.civilization.Model.ModulEnums.LandType.FOREST
                        && tile.getEnemy() == null && tile.getCivilizedUnit() == null) {
                    Game.instance.bearDen = hex;
                    return;
                }
            }
        }
    }

    /** All living bears on the map. */
    private static ArrayList<Enemy> findBears() {
        ArrayList<Enemy> bears = new ArrayList<>();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Enemy e = Game.instance.map[i][j].getEnemy();
                if (e != null && e.getType() == sut.civilization.Model.ModulEnums.EnemyType.BEAR)
                    bears.add(e);
            }
        return bears;
    }

    /** Player units within `radius` hexes of `center`, nearest first, civilians first. */
    private static ArrayList<CivilizedUnit> unitsNear(Pair<Integer, Integer> center, int radius) {
        ArrayList<CivilizedUnit> civilians = new ArrayList<>();
        ArrayList<CivilizedUnit> military = new ArrayList<>();
        for (int r = 1; r <= radius; r++) {
            for (Pair<Integer, Integer> hex : hexesInRadius(center, r)) {
                if (!Pair.isValid(hex)) continue;
                CivilizedUnit unit = Game.instance.map[hex.x][hex.y].getCivilizedUnit();
                if (unit == null || civilians.contains(unit) || military.contains(unit)) continue;
                if (unit.getCivilizedUnitType().isMilitary) military.add(unit);
                else civilians.add(unit);
            }
        }
        // P2-57 priority: the nearest CIVILIAN first, only then military
        ArrayList<CivilizedUnit> ordered = new ArrayList<>(civilians);
        ordered.addAll(military);
        return ordered;
    }

    /**
     * Runs once per game turn (after the barbarians). Handles den spawning/despawning
     * and gives every bear on the map its turn: walk up to 2 steps (AP 2) toward its
     * prey, then attack once. Battle reports land in CombatController.lastEnemyBattles.
     */
    public static void bearTurn() {
        ensureBearDen();
        Pair<Integer, Integer> den = Game.instance.bearDen;

        if (den != null && bearDenEnabled) {
            ArrayList<Enemy> bears = findBears();
            ArrayList<CivilizedUnit> nearDen = unitsNear(den, BEAR_HOME_RADIUS);

            if (!bears.isEmpty() && nearDen.isEmpty()) {
                // nobody left to hunt: the bears go back into the forest
                for (Enemy bear : bears) CombatController.removeEnemy(bear);
                Game.instance.bearCalmUntilTurn = Game.instance.getTurn() + BEAR_CALM_TURNS;
                notifyTurn("The bears returned to the forest. The area is calm for "
                        + BEAR_CALM_TURNS + " turns.");
            } else if (!nearDen.isEmpty() && Game.instance.getTurn() >= Game.instance.bearCalmUntilTurn) {
                int wanted = nearDen.size() >= BEAR_CROWD_UNITS ? BEAR_MAX : 1;
                for (int need = wanted - bears.size(); need > 0; need--) {
                    Pair<Integer, Integer> spot = freeSpotNear(den);
                    if (spot == null) break;
                    Game.instance.map[spot.x][spot.y].setEnemy(
                            new Enemy(sut.civilization.Model.ModulEnums.EnemyType.BEAR, spot));
                    notifyTurn("A BEAR came out of its den at (" + spot.x + "," + spot.y + ")!");
                }
            }
        }

        // every bear (den bears and debug-spawned ones) moves and attacks
        for (Enemy bear : findBears()) {
            ArrayList<CivilizedUnit> prey = unitsNear(bear.getLocation(), BEAR_HOME_RADIUS);
            if (prey.isEmpty()) continue;
            CivilizedUnit target = prey.get(0);

            // AP 2: up to two greedy steps toward the prey, stop when adjacent
            for (int step = 0; step < 2; step++) {
                if (CombatController.hexDistance(bear.getLocation(), target.getLocation()) <= 1) break;
                moveBearOneStep(bear, target.getLocation());
            }
            if (CombatController.hexDistance(bear.getLocation(), target.getLocation()) <= 1) {
                CombatController.lastEnemyBattles.add(CombatController.bearAttacksUnit(bear, target));
            }
        }
    }

    /** The den hex itself if free, otherwise the nearest free walkable hex around it. */
    private static Pair<Integer, Integer> freeSpotNear(Pair<Integer, Integer> den) {
        ArrayList<Pair<Integer, Integer>> candidates = new ArrayList<>();
        candidates.add(den);
        candidates.addAll(hexesInRadius(den, 1));
        for (Pair<Integer, Integer> hex : candidates) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            if (!tile.getLandType().isWalkable) continue;
            if (tile.getCivilizedUnit() != null || tile.getEnemy() != null) continue;
            return hex;
        }
        return null;
    }

    /** One step toward `goal`: the walkable, empty neighbor that gets the bear closest. */
    private static void moveBearOneStep(Enemy bear, Pair<Integer, Integer> goal) {
        Pair<Integer, Integer> best = null;
        int bestDistance = CombatController.hexDistance(bear.getLocation(), goal);
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(bear.getLocation())) {
            if (!Pair.isValid(nb)) continue;
            Land tile = Game.instance.map[nb.x][nb.y];
            if (!tile.getLandType().isWalkable) continue;
            if (tile.getCivilizedUnit() != null || tile.getEnemy() != null) continue;
            int distance = CombatController.hexDistance(nb, goal);
            if (distance < bestDistance) { bestDistance = distance; best = nb; }
        }
        if (best == null) return;
        Game.instance.map[bear.getLocation().x][bear.getLocation().y].setEnemy(null);
        Game.instance.map[best.x][best.y].setEnemy(bear);
        bear.getLocation().x = best.x;
        bear.getLocation().y = best.y;
    }

    /** P2-08: a unit at 0 HP is removed from the game immediately. */
    static void removeDeadUnit(CivilizedUnit unit) {
        if (unit.isStationed()) {
            unit.getStationedIn().removeWorker(unit);
            unit.leaveStation();
        }
        Pair<Integer, Integer> loc = unit.getLocation();
        if (Pair.isValid(loc) && Game.instance.map[loc.x][loc.y].getCivilizedUnit() == unit)
            Game.instance.map[loc.x][loc.y].setCivilizedUnit(null);
        unit.getOwnerNation().removeUnit(unit);
        if (selectedCivilizedUnit == unit) selectedCivilizedUnit = null;
        notifyTurn(unit.getCivilizedUnitType().name + " at (" + loc.x + "," + loc.y + ") was killed.");
    }
}

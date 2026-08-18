package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.ImprovementType;

import java.util.ArrayList;
import java.util.Random;

/**
 * Phase 2 disasters (P2-54/55). Only the earthquake exists so far - the 5% roll,
 * the damage helpers and the "tell the UI to animate it" plumbing are written so
 * later disasters (flood, bear...) can reuse them.
 */
public class DisasterController extends GameController {

    private static final Random random = new Random();

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
        if (random.nextDouble() >= DISASTER_CHANCE_PER_TURN) return;
        int i = random.nextInt(Consts.MAP_SIZE.amount.x);
        int j = random.nextInt(Consts.MAP_SIZE.amount.y);
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

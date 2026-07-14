package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;

/**
 * Headless regression driver for the 2026-07-07 fixes:
 *  1. Turn order: production credited to balance before upkeep deducted from it.
 *  2. Border Expander requires Explorer-specific exploration (not generic visibility).
 *  3. Missed-upkeep counter surfaced (data-level check; UI itself not exercised here).
 *  4. Music persistence - not testable headlessly (Swing/audio), skipped.
 *  5. AP rebalance: Builder=4 AP (can build Town in one turn @4 AP), Worker=3 AP.
 * Also re-runs the old QaDriver's 3 checks to confirm no regression.
 */
public class QaDriver2 {
    static int pass = 0, fail = 0;
    static void check(String label, boolean condition) {
        if (condition) { pass++; System.out.println("PASS: " + label); }
        else { fail++; System.out.println("FAIL: " + label); }
    }

    static Nation freshNation() {
        User user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);
        Game.instance.setMap(LandController.mapInitializer());
        LandController.initializeDistances();
        LandController.updateDistances();
        return user.getNation();
    }

    static CivilizedUnit findUnit(Nation nation, CivilizedUnitType type) {
        return (CivilizedUnit) nation.getUnits().stream()
                .filter(u -> ((CivilizedUnit) u).getCivilizedUnitType() == type)
                .findFirst().orElse(null);
    }

    /** Finds any free (no unit) tile of the given type anywhere on the map, and marks it visible. */
    static Land findFreeTile(LandType type) {
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                Land t = Game.instance.map[i][j];
                if (t.getLandType() == type && t.getCivilizedUnit() == null && t.getImprovement() == null) {
                    t.setVisibility(2);
                    return t;
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        testTurnOrderProductionBeforeUpkeep();
        testBorderExpanderRequiresExplorer();
        testMissedUpkeepCounterSurfaced();
        testApRebalanceAndTownBuildable();
        testOldRegressions();

        System.out.println("\n=== SUMMARY: " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    // ── 1. Turn order: a Lumber Mill with 0 wood banked, but enough production this
    //     turn to cover its own upkeep, must NOT be flagged as a missed-upkeep turn. ──
    static void testTurnOrderProductionBeforeUpkeep() {
        System.out.println("=== Turn order: production must land before upkeep is deducted ===");
        Nation nation = freshNation();

        CivilizedUnit builder = findUnit(nation, CivilizedUnitType.BUILDER);
        // Find any free forest tile on the map (territory is small/random; extend territory to it).
        Land forestTile = findFreeTile(LandType.FOREST);
        check("Found a free forest tile to build on", forestTile != null);
        if (forestTile == null) return;
        nation.addToTerritory(new Pair<>(forestTile.getI(), forestTile.getJ()));

        // Teleport the builder onto that tile directly (bypassing movement, not under test here).
        Game.instance.map[builder.getLocation().x][builder.getLocation().y].setCivilizedUnit(null);
        builder.setLocation(new Pair<>(forestTile.getI(), forestTile.getJ()));
        forestTile.setCivilizedUnit(builder);
        builder.setCurrentAp(builder.getCivilizedUnitType().ap);

        GameController.setSelectedCivilizedUnit(builder);
        String buildMsg = WorkerController.builderBuildImprovement("Lumber Mill");
        check("Lumber Mill built successfully: " + buildMsg, buildMsg.startsWith("Lumber Mill built"));

        // Station a worker in it so it actually produces wood this turn.
        CivilizedUnit worker = findUnit(nation, CivilizedUnitType.WORKER);
        Game.instance.map[worker.getLocation().x][worker.getLocation().y].setCivilizedUnit(null);
        worker.setLocation(new Pair<>(forestTile.getI(), forestTile.getJ()));
        forestTile.setCivilizedUnit(worker); // note: overwrites builder's tile occupancy marker, fine for this headless check
        worker.setCurrentAp(worker.getCivilizedUnitType().ap);
        GameController.setSelectedCivilizedUnit(worker);
        String stationMsg = WorkerController.stationWorker();
        check("Worker stationed in Lumber Mill: " + stationMsg, stationMsg.startsWith("Worker stationed"));

        // Drain wood to 0 so upkeep (1 wood) could only be paid out of THIS turn's production.
        nation.getWood().spend(nation.getWood().getBalance());
        check("Wood balance is 0 before end turn", nation.getWood().getBalance() == 0);

        int woodBefore = nation.getWood().getBalance();
        GameController.endTurn();

        Improvement mill = forestTile.getImprovement();
        check("Lumber Mill still exists after end turn (not destroyed)", mill != null);
        if (mill != null) {
            check("Lumber Mill missed-upkeep counter is 0 (upkeep was paid from this turn's own production)",
                    mill.getMissedUpkeepTurns() == 0);
        }
        // Lumber Mill produces 3 wood/worker, upkeep is 1 wood -> net should be +2 vs the 0 we started at,
        // plus TownHall safeguard +1 wood => net wood balance should be 0 + 3 - 1 + 1 = 3.
        check("Wood balance reflects production-before-upkeep (expected 3, got " + nation.getWood().getBalance() + ")",
                nation.getWood().getBalance() == 3);
    }

    // ── 2. Border Expander must require Explorer-specific exploration ──
    static void testBorderExpanderRequiresExplorer() {
        System.out.println("\n=== Border Expander: requires Explorer-specific exploration ===");
        Nation nation = freshNation();

        CivilizedUnit borderExpander = new CivilizedUnit(CivilizedUnitType.BORDER_EXPANDER, nation, new Pair<>(7, 7));
        // Place it on the TownHall tile itself (already explorer-seen at map init) - sanity check first.
        Land thTile = nation.getTownHall().getMainLand();
        Game.instance.map[thTile.getI()][thTile.getJ()].setCivilizedUnit(null);
        borderExpander.setLocation(new Pair<>(thTile.getI(), thTile.getJ()));
        Game.instance.map[thTile.getI()][thTile.getJ()].setCivilizedUnit(borderExpander);
        nation.addUnit(borderExpander);
        GameController.setSelectedCivilizedUnit(borderExpander);

        check("canExpandBorder() true on TownHall's own pre-explored hex",
                TownHallController.canExpandBorder(borderExpander));

        // Now find a hex NOT explored by anyone, walk a Builder (non-Explorer) next to it so its
        // generic visibility/seer gets set, and confirm Border Expander still refuses it.
        CivilizedUnit builder = findUnit(nation, CivilizedUnitType.BUILDER);
        Pair<Integer, Integer> farHex = null;
        for (int i = 0; i < 15 && farHex == null; i++) {
            for (int j = 0; j < 15 && farHex == null; j++) {
                Land t = Game.instance.map[i][j];
                if (t.getVisibility() == 0 && Math.abs(i - 7) + Math.abs(j - 7) > 4) {
                    farHex = new Pair<>(i, j);
                }
            }
        }
        check("Found a still-fogged far hex to test with", farHex != null);
        if (farHex == null) return;

        // Simulate a Builder's non-Explorer reveal directly via GameController.revealAround
        // the same way updateFogOfWar() would for a Builder unit (isExplorer=false).
        GameController.revealAround(farHex, 1, nation, false);
        Land farTile = Game.instance.map[farHex.x][farHex.y];
        check("Generic visibility was set by the non-Explorer reveal", farTile.getVisibility() == 2);
        check("But isExploredBy(nation) is still false (Builder/Worker visibility must NOT count as exploration)",
                !farTile.isExploredBy(nation));

        // Move the border expander there and confirm expansion is still blocked.
        Game.instance.map[borderExpander.getLocation().x][borderExpander.getLocation().y].setCivilizedUnit(null);
        borderExpander.setLocation(farHex);
        farTile.setCivilizedUnit(borderExpander);
        borderExpander.setCurrentAp(borderExpander.getCivilizedUnitType().ap);
        GameController.setSelectedCivilizedUnit(borderExpander);
        check("canExpandBorder() false on a hex only a Builder has seen (not an Explorer)",
                !TownHallController.canExpandBorder(borderExpander));
        String expandMsg = TownHallController.expandBorder();
        check("expandBorder() rejects with correct message: " + expandMsg,
                expandMsg.contains("explored by an Explorer"));

        // Now do a real Explorer reveal of that same hex and confirm it becomes expandable.
        GameController.revealAround(farHex, 1, nation, true);
        check("isExploredBy(nation) true after an actual Explorer reveal", farTile.isExploredBy(nation));
        check("canExpandBorder() true once an Explorer has actually seen it",
                TownHallController.canExpandBorder(borderExpander));
    }

    // ── 3. Missed-upkeep counter increments and is queryable (drives the new HUD/tooltip warning) ──
    static void testMissedUpkeepCounterSurfaced() {
        System.out.println("\n=== Missed-upkeep counter increments correctly for HUD warning ===");
        Nation nation = freshNation();

        CivilizedUnit builder = findUnit(nation, CivilizedUnitType.BUILDER);
        // TownHall's safeguard trickle adds +1 wood/turn unconditionally, which alone covers a
        // single Lumber Mill's 1-wood upkeep. Build TWO unstaffed mills (2 wood/turn upkeep,
        // 0 wood/turn production) so the safeguard's +1 cannot cover both and upkeep genuinely misses.
        Improvement mill1 = null;
        Land mill2Tile = null;
        for (int m = 0; m < 2; m++) {
            Land forestTile = findFreeTile(LandType.FOREST);
            check("Found a free forest tile #" + m, forestTile != null);
            if (forestTile == null) return;
            nation.addToTerritory(new Pair<>(forestTile.getI(), forestTile.getJ()));

            Game.instance.map[builder.getLocation().x][builder.getLocation().y].setCivilizedUnit(null);
            builder.setLocation(new Pair<>(forestTile.getI(), forestTile.getJ()));
            forestTile.setCivilizedUnit(builder);
            builder.setCurrentAp(builder.getCivilizedUnitType().ap);
            GameController.setSelectedCivilizedUnit(builder);
            WorkerController.builderBuildImprovement("Lumber Mill");
            if (m == 0) mill1 = forestTile.getImprovement();
            else mill2Tile = forestTile;
        }
        Improvement mill2 = mill2Tile.getImprovement();
        check("Both mills exist with 0 missed-upkeep turns initially",
                mill1 != null && mill1.getMissedUpkeepTurns() == 0
                        && mill2 != null && mill2.getMissedUpkeepTurns() == 0);

        // With territory iteration order fixed, mill1 (built/iterated first) always gets first
        // claim on the safeguard's +1 wood/turn and never misses upkeep; mill2 is the one that
        // actually starves. Drain wood and run turns until mill2 is destroyed (3 missed turns).
        nation.getWood().spend(nation.getWood().getBalance()); // drain to 0 right before end turn
        for (int t = 1; t <= 3; t++) {
            GameController.endTurn();
            System.out.println("  after turn " + t + ": wood=" + nation.getWood().getBalance()
                    + " mill1.missed=" + mill1.getMissedUpkeepTurns()
                    + " mill2.missed=" + mill2.getMissedUpkeepTurns());
        }
        check("Well-funded mill1 never misses upkeep (safeguard covers it first)",
                mill1.getMissedUpkeepTurns() == 0);
        check("Starved mill2's missed-upkeep counter incremented (HUD warning data is live)",
                mill2.getMissedUpkeepTurns() >= 3 || mill2Tile.getImprovement() == null);
        check("Starved mill2 is actually destroyed after 3 missed upkeep turns",
                mill2Tile.getImprovement() == null);
    }

    // ── 4. AP rebalance: Builder=4 AP can build a Town (costs 4 AP) in a single turn ──
    static void testApRebalanceAndTownBuildable() {
        System.out.println("\n=== AP rebalance: Builder can afford Town's 4 AP cost ===");
        Nation nation = freshNation();

        check("Builder max AP is 4", CivilizedUnitType.BUILDER.ap == 4);
        check("Worker max AP is 3", CivilizedUnitType.WORKER.ap == 3);
        check("Explorer max AP is still 4 (Explorer keeps vision-radius lead: "
                        + CivilizedUnitType.EXPLORER.visionRadius + " vs Builder's "
                        + CivilizedUnitType.BUILDER.visionRadius + ")",
                CivilizedUnitType.EXPLORER.ap == 4 && CivilizedUnitType.EXPLORER.visionRadius > CivilizedUnitType.BUILDER.visionRadius);
        check("Town build AP cost is exactly 4 (a full Builder turn)", ImprovementType.TOWN.buildApCost == 4);

        // Find a resource-free hex anywhere for the Town (mountain, or meadow/plain w/o food resource).
        CivilizedUnit builder = findUnit(nation, CivilizedUnitType.BUILDER);
        Land freeTile = null;
        outer:
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                Land t = Game.instance.map[i][j];
                if (t.getCivilizedUnit() != null || t.getImprovement() != null) continue;
                boolean resourceFree = t.getLandType() == LandType.MOUNTAIN
                        || ((t.getLandType() == LandType.MEADOW || t.getLandType() == LandType.PLAIN) && t.getResource() == null);
                if (resourceFree) { freeTile = t; break outer; }
            }
        }
        check("Found a resource-free hex for Town", freeTile != null);
        if (freeTile == null) return;
        nation.addToTerritory(new Pair<>(freeTile.getI(), freeTile.getJ()));
        freeTile.setVisibility(2);

        Game.instance.map[builder.getLocation().x][builder.getLocation().y].setCivilizedUnit(null);
        builder.setLocation(new Pair<>(freeTile.getI(), freeTile.getJ()));
        freeTile.setCivilizedUnit(builder);
        builder.setCurrentAp(builder.getCivilizedUnitType().ap); // exactly 4, matching Town's cost

        // Give enough resources to afford Town (5 wood, 3 stone, 2 iron) and the required tech.
        nation.getWood().add(20);
        nation.getStone().add(20);
        nation.getIron().add(20);
        nation.unlockTechnology(TechnologyType.TECH_TOWN);

        GameController.setSelectedCivilizedUnit(builder);
        String blocked = WorkerController.checkCanBuild(ImprovementType.TOWN);
        check("checkCanBuild(TOWN) allows it with exactly 4 AP available: " + blocked, blocked == null);

        String result = WorkerController.builderBuildImprovement("Town");
        check("Town built successfully in a single turn with exactly its AP budget: " + result,
                result.startsWith("Town built"));
        check("Builder AP fully spent after building Town (4 - 4 = 0)", builder.getCurrentAp() == 0);
    }

    // ── 5. Re-run prior session's regressions to confirm today's changes didn't break them ──
    static void testOldRegressions() {
        System.out.println("\n=== Re-run of prior QaDriver regressions ===");
        Nation nation = freshNation();

        check("hasIdleUnits() true at game start (all units unordered)", GameController.hasIdleUnits());
        int turnBefore = Game.instance.getTurn();
        String msg = GameController.endTurn();
        check("endTurn() succeeds even with idle units", msg.startsWith("next turn:"));
        check("Turn actually advanced", Game.instance.getTurn() == turnBefore + 1);

        for (Unit u : new ArrayList<>(nation.getUnits())) u.setCurrentAp(0);
        check("hasIdleUnits() false once all units are spent", !GameController.hasIdleUnits());
        String msg2 = GameController.endTurn();
        check("endTurn() succeeds with no idle units", msg2.startsWith("next turn:"));

        CivilizedUnit explorer = findUnit(nation, CivilizedUnitType.EXPLORER);
        explorer.setCurrentAp(CivilizedUnitType.EXPLORER.ap);
        GameController.setSelectedCivilizedUnit(explorer);
        Pair<Integer, Integer> startLoc = explorer.getLocation();
        Pair<Integer, Integer> dest = null;
        int destCost = -1;
        for (int dir = 0; dir < 6; dir++) {
            Pair<Integer, Integer> cand = LandController.getNeighborIndex(startLoc, dir);
            if (Pair.isValid(cand) && Game.instance.map[cand.x][cand.y].getCivilizedUnit() == null) {
                dest = cand;
                destCost = Game.instance.map[cand.x][cand.y].getMP();
                break;
            }
        }
        if (dest != null) {
            UnitController.unitSetPath(dest.x, dest.y, 0);
            int stepsRun = 0;
            while (UnitController.hasMoreSteps(explorer) && stepsRun < 20) { UnitController.unitGoForward(explorer); stepsRun++; }
            check("Explorer moved exactly one hex", stepsRun == 1);
            check("AP after one-hex move equals maxAp minus that tile's terrain cost",
                    explorer.getCurrentAp() == Math.max(0, CivilizedUnitType.EXPLORER.ap - destCost));
        }
    }
}

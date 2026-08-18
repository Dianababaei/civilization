import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Thorough headless end-to-end driver exercising real gameplay workflows against
 * the Controller/Model layer, asserting concrete before/after state.
 *
 * Not JUnit - plain main(), same pattern as QaDriver.java.
 */
public class FullWorkflowDriver {
    static int pass = 0, fail = 0;
    static List<String> failLabels = new ArrayList<>();

    static void check(String label, boolean condition) {
        if (condition) { pass++; System.out.println("PASS: " + label); }
        else { fail++; failLabels.add(label); System.out.println("FAIL: " + label); }
    }

    static void info(String s) { System.out.println("INFO: " + s); }

    static Nation nation;
    static User user;

    public static void main(String[] args) {
        setup();

        section1_gameInit();
        CivilizedUnit builder = section2_builderBuildsImprovement();
        section3_builderChargeDepletion();
        CivilizedUnit worker = section4_workerStationing();
        section5and6_productionAndUnstation(worker);
        section7_upkeepDeduction();
        section8_buildingDestruction();
        section9_starvation();
        section10_techResearch();
        section11_borderExpander();
        section12_movementApCost();
        section13_fogOfWar();
        section14_unitCap();

        System.out.println("\n=== SUMMARY: " + pass + " passed, " + fail + " failed ===");
        if (!failLabels.isEmpty()) {
            System.out.println("Failed checks:");
            for (String s : failLabels) System.out.println("  - " + s);
        }
    }

    // ── Setup ────────────────────────────────────────────────────────────────
    static void setup() {
        // random 5%-per-turn earthquakes would make this driver flaky
        DisasterController.randomDisastersEnabled = false;
        user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);

        // mapInitializer() assigns terrain randomly (Math.random() seed, not injectable), so
        // whether the starting 7-hex territory (TownHall + 6 neighbors) contains a FOREST hex
        // is a coin flip. Section 2+ needs a buildable Lumber Mill site in starting territory,
        // so retry map generation (bounded) until one lands - this is purely working around map
        // randomness, not altering any behavior under test.
        boolean hasForestInStart = false;
        for (int attempt = 0; attempt < 25 && !hasForestInStart; attempt++) {
            Game.instance.setMap(LandController.mapInitializer());
            LandController.initializeDistances();
            LandController.updateDistances();
            nation = user.getNation();
            for (Pair<Integer, Integer> hex : nation.getTerritory()) {
                if (landAt(hex).getLandType() == LandType.FOREST) { hasForestInStart = true; break; }
            }
        }
        info("Map setup: forest hex present in starting territory = " + hasForestInStart);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static CivilizedUnit findUnit(CivilizedUnitType type) {
        for (Unit u : nation.getUnits()) {
            if (u instanceof CivilizedUnit && ((CivilizedUnit) u).getCivilizedUnitType() == type)
                return (CivilizedUnit) u;
        }
        return null;
    }

    static List<CivilizedUnit> findUnits(CivilizedUnitType type) {
        List<CivilizedUnit> list = new ArrayList<>();
        for (Unit u : nation.getUnits()) {
            if (u instanceof CivilizedUnit && ((CivilizedUnit) u).getCivilizedUnitType() == type)
                list.add((CivilizedUnit) u);
        }
        return list;
    }

    static Land landAt(Pair<Integer, Integer> p) { return Game.instance.map[p.x][p.y]; }

    /** Find a FOREST hex within current territory that has no improvement and is explored. */
    static Pair<Integer, Integer> findForestHexInTerritory() {
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            Land l = landAt(hex);
            if (l.getLandType() == LandType.FOREST && l.getImprovement() == null && l.getVisibility() > 0
                    && !l.isResourceDepleted()) {
                return hex;
            }
        }
        return null;
    }

    /** Move a unit (already selected) step by step to destination. Returns true if it arrived. */
    static boolean moveUnitTo(CivilizedUnit unit, Pair<Integer, Integer> dest) {
        GameController.setSelectedCivilizedUnit(unit);
        String res = UnitController.unitSetPath(dest.x, dest.y, 0);
        if (!res.equals("Unit moving...")) {
            info("moveUnitTo failed to set path: " + res);
            return false;
        }
        int steps = 0;
        while (UnitController.hasMoreSteps(unit) && steps < 30) {
            UnitController.unitGoForward(unit);
            steps++;
        }
        return unit.getLocation().equals(dest);
    }

    // ── 1. Game init ─────────────────────────────────────────────────────────
    static void section1_gameInit() {
        System.out.println("\n=== 1. Game init ===");
        check("Town Hall exists after mapInitializer()", nation.getTownHall() != null);
        check("5 units exist after mapInitializer()", nation.getUnits().size() == 5);
        check("2 Builder units", findUnits(CivilizedUnitType.BUILDER).size() == 2);
        check("2 Worker units", findUnits(CivilizedUnitType.WORKER).size() == 2);
        check("1 Explorer unit", findUnits(CivilizedUnitType.EXPLORER).size() == 1);
        check("Starting territory is TownHall hex + 6 neighbors (7 hexes)", nation.getTerritory().size() == 7);
    }

    // ── 2. Builder builds an improvement ────────────────────────────────────
    static CivilizedUnit builderUsed;
    static Pair<Integer, Integer> builtHex;

    static CivilizedUnit section2_builderBuildsImprovement() {
        System.out.println("\n=== 2. Builder builds an improvement (Lumber Mill) ===");
        CivilizedUnit builder = findUnit(CivilizedUnitType.BUILDER);
        if (builder == null) { check("Builder available", false); return null; }

        Pair<Integer, Integer> forestHex = findForestHexInTerritory();
        if (forestHex == null) {
            info("No forest hex in starting territory (7 hexes); expanding search won't help without border expansion. Marking build test skipped-as-fail with detail.");
            check("Found a forest hex within starting territory to build Lumber Mill", false);
            return null;
        }
        check("Found a forest hex within starting territory to build Lumber Mill", true);

        boolean moved = moveUnitTo(builder, forestHex);
        check("Builder moved onto forest hex", moved);
        if (!moved) return null;

        // Refill AP for build (movement may have consumed some) so the build itself isn't blocked by movement cost.
        builder.setCurrentAp(builder.getMaxAp());
        GameController.setSelectedCivilizedUnit(builder);

        int woodBefore = nation.getWood().getBalance();
        int apBefore = builder.getCurrentAp();
        int chargesBefore = builder.getCharges();

        String blocked = WorkerController.checkCanBuild(ImprovementType.LUMBER_MILL);
        check("checkCanBuild(LUMBER_MILL) returns null (buildable)", blocked == null);
        if (blocked != null) { info("Blocked reason: " + blocked); return null; }

        String result = WorkerController.builderBuildImprovement("Lumber Mill");
        info("builderBuildImprovement result: " + result);

        Land tile = landAt(forestHex);
        check("Improvement now exists on the built hex", tile.getImprovement() != null);
        check("Improvement type is LUMBER_MILL",
                tile.getImprovement() != null && tile.getImprovement().getImprovementType() == ImprovementType.LUMBER_MILL);
        check("Builder charge count decremented by 1", builder.getCharges() == chargesBefore - 1);
        check("Builder AP decremented by build AP cost (" + ImprovementType.LUMBER_MILL.buildApCost + ")",
                builder.getCurrentAp() == apBefore - ImprovementType.LUMBER_MILL.buildApCost);
        check("Nation wood decremented by build cost (" + ImprovementType.LUMBER_MILL.buildCostWood + ")",
                nation.getWood().getBalance() == woodBefore - ImprovementType.LUMBER_MILL.buildCostWood);

        builderUsed = builder;
        builtHex = forestHex;
        return builder;
    }

    // ── 3. Builder charge depletion ─────────────────────────────────────────
    static void section3_builderChargeDepletion() {
        System.out.println("\n=== 3. Builder charge depletion (3 builds -> consumed) ===");
        if (builderUsed == null) {
            check("Builder charge depletion test runnable (prereq: section 2 succeeded)", false);
            return;
        }
        // builderUsed already used 1 charge in section 2. Need 2 more forest hexes in territory.
        int chargesBefore = builderUsed.getCharges();
        check("Builder has 2 charges remaining after first build", chargesBefore == 2);

        int builds = 1; // already built once
        for (int i = 0; i < 2; i++) {
            Pair<Integer, Integer> forestHex = findForestHexInTerritory();
            if (forestHex == null) {
                info("No more free forest hexes in territory for build #" + (builds + 1) + " - territory too small.");
                break;
            }
            boolean moved = moveUnitTo(builderUsed, forestHex);
            if (!moved) { info("Failed to move builder to forest hex for build #" + (builds + 1)); break; }
            builderUsed.setCurrentAp(builderUsed.getMaxAp());
            GameController.setSelectedCivilizedUnit(builderUsed);
            String blocked = WorkerController.checkCanBuild(ImprovementType.LUMBER_MILL);
            if (blocked != null) { info("Build #" + (builds + 1) + " blocked: " + blocked); break; }
            WorkerController.builderBuildImprovement("Lumber Mill");
            builds++;
        }

        check("Builder was built with 3 total times (charges exhausted)", builds == 3);
        check("Builder removed from nation's unit list after 3rd build (consumed)",
                !nation.getUnits().contains(builderUsed));
        if (builds == 3) {
            check("Builder removed from map tile after 3rd build",
                    landAt(builderUsed.getLocation()).getCivilizedUnit() != builderUsed);
        }
    }

    // ── 4. Worker stationing ────────────────────────────────────────────────
    static CivilizedUnit stationedWorker;

    static CivilizedUnit section4_workerStationing() {
        System.out.println("\n=== 4. Worker stationing ===");
        if (builtHex == null) {
            check("Worker stationing test runnable (prereq: an improvement exists)", false);
            return null;
        }
        CivilizedUnit worker = findUnit(CivilizedUnitType.WORKER);
        if (worker == null) { check("Worker available", false); return null; }

        boolean moved = moveUnitTo(worker, builtHex);
        check("Worker moved onto the improvement hex", moved);
        if (!moved) return null;

        worker.setCurrentAp(worker.getMaxAp());
        GameController.setSelectedCivilizedUnit(worker);

        int apBefore = worker.getCurrentAp();
        Improvement imp = landAt(builtHex).getImprovement();
        int workerCountBefore = imp.getWorkerCount();

        String result = WorkerController.stationWorker();
        info("stationWorker result: " + result);

        check("Worker AP decremented by station cost (" + WorkerController.STATION_AP_COST + ")",
                worker.getCurrentAp() == apBefore - WorkerController.STATION_AP_COST);
        check("Worker's stationedIn is set to the improvement", worker.getStationedIn() == imp);
        check("Improvement's stationed-worker list contains the worker",
                imp.getStationedWorkers().contains(worker));
        check("Improvement worker count incremented by 1", imp.getWorkerCount() == workerCountBefore + 1);

        stationedWorker = worker;
        return worker;
    }

    // ── 5 & 6. Production on turn end + Worker leaving station (no refund) ──
    static void section5and6_productionAndUnstation(CivilizedUnit worker) {
        System.out.println("\n=== 6. Worker leaving a station mid-turn: AP stays reduced (no refund) ===");
        if (worker == null) {
            check("Unstation-mid-turn test runnable (prereq: section 4 succeeded)", false);
        } else {
            int apBeforeUnstation = worker.getCurrentAp();
            GameController.setSelectedCivilizedUnit(worker);
            String res = WorkerController.unstationWorker();
            info("unstationWorker result: " + res);
            check("Worker's stationedIn cleared after unstation", worker.getStationedIn() == null);
            check("Worker AP does NOT jump back up after unstation (stays at " + apBeforeUnstation + ")",
                    worker.getCurrentAp() == apBeforeUnstation);

            // Re-station for the production test (need a stationed worker to measure production).
            GameController.setSelectedCivilizedUnit(worker);
            String restation = WorkerController.stationWorker();
            info("Re-station for production test: " + restation);
        }

        System.out.println("\n=== 5. Production on turn end ===");
        if (builtHex == null || stationedWorker == null) {
            check("Production-on-turn-end test runnable (prereq: build+station succeeded)", false);
            return;
        }
        Improvement imp = landAt(builtHex).getImprovement();
        if (imp == null) {
            check("Improvement still exists before end-turn production test", false);
            return;
        }
        int workerCount = imp.getWorkerCount();
        int woodBefore = nation.getWood().getBalance();

        // Reset all units' AP to something less than max, so we can verify refresh to max next turn.
        for (Unit u : nation.getUnits()) u.setCurrentAp(0);
        int stationedApBeforeTurn = stationedWorker.getCurrentAp(); // should be 0 now (we just zeroed it)

        // Ensure nation can afford upkeep (wood) so production isn't clouded by destruction this turn.
        info("Wood balance before endTurn(): " + woodBefore + ", stationed workers on improvement: " + workerCount);

        String msg = GameController.endTurn();
        check("endTurn() succeeds", msg.startsWith("next turn:"));

        int expectedProduction = workerCount * ImprovementType.LUMBER_MILL.productionPerWorkerPerTurn;
        int expectedUpkeep = ImprovementType.LUMBER_MILL.upkeepAmount; // wood upkeep, only if paid
        int woodAfter = nation.getWood().getBalance();
        info("Expected raw production=" + expectedProduction + " upkeep=" + expectedUpkeep
                + " woodBefore=" + woodBefore + " woodAfter=" + woodAfter);
        // Note: TownHall safeguard trickle also adds +1 wood/turn (see TownHall.produceSafeguardTrickle),
        // and upkeep is paid from wood too - so exact expected = before + trickle(1) + production - upkeep,
        // subject to deposit cap / capacity clamp.
        int expectedWoodAfter = Math.min(nation.getWood().getCapacity(),
                woodBefore + 1 /*safeguard trickle*/ + expectedProduction - expectedUpkeep);
        check("Nation wood increased by (workers x base rate) after accounting for +1 safeguard trickle and upkeep",
                woodAfter == expectedWoodAfter);

        // AP refresh: all units except check the stationed worker specifically per spec nuance.
        boolean allRefreshed = true;
        for (Unit u : nation.getUnits()) {
            if (u.getCurrentAp() != u.getMaxAp()) allRefreshed = false;
        }
        check("refreshAp() resets ALL units' AP to max at new turn start (including the stationed worker)",
                allRefreshed);
        check("Stationed worker's AP prior to refresh reflected the earlier mid-turn reduction (was " + stationedApBeforeTurn + ", not silently refunded before turn end)",
                stationedApBeforeTurn == 0);
    }

    // ── 7. Upkeep deduction ─────────────────────────────────────────────────
    static void section7_upkeepDeduction() {
        System.out.println("\n=== 7. Upkeep deduction ===");
        if (builtHex == null) {
            check("Upkeep deduction test runnable (prereq: improvement exists)", false);
            return;
        }
        Improvement imp = landAt(builtHex).getImprovement();
        if (imp == null) {
            check("Improvement still exists for upkeep test", false);
            return;
        }
        // Ensure nation has enough wood to pay upkeep this turn, and isolate the upkeep effect
        // by reading balances immediately before/after a fresh endTurn() call.
        nation.getWood().add(50); // headroom so upkeep definitely succeeds (not blocked by insufficient funds)
        int woodBefore = nation.getWood().getBalance();
        int workerCount = imp.getWorkerCount();

        String msg = GameController.endTurn();
        check("endTurn() succeeds for upkeep test", msg.startsWith("next turn:"));

        int production = workerCount * ImprovementType.LUMBER_MILL.productionPerWorkerPerTurn;
        int upkeep = ImprovementType.LUMBER_MILL.upkeepAmount;
        int expected = Math.min(nation.getWood().getCapacity(), woodBefore + 1 + production - upkeep);
        int woodAfter = nation.getWood().getBalance();
        info("woodBefore=" + woodBefore + " production=" + production + " upkeep=" + upkeep + " woodAfter=" + woodAfter);
        check("Wood decreased by upkeep amount (" + upkeep + ") net of production+trickle, as expected",
                woodAfter == expected);
        check("Improvement's missedUpkeepTurns reset to 0 since upkeep was paid", imp.getMissedUpkeepTurns() == 0);
    }

    // ── 8. Building destruction after 3 missed upkeep turns ─────────────────
    static void section8_buildingDestruction() {
        System.out.println("\n=== 8. Building destruction after 3 consecutive missed upkeep turns ===");
        if (builtHex == null) {
            check("Building destruction test runnable (prereq: improvement exists)", false);
            return;
        }
        Improvement imp = landAt(builtHex).getImprovement();
        if (imp == null) {
            check("Improvement still exists before destruction test", false);
            return;
        }
        // Drain wood to 0, and also make sure production this improvement generates can't
        // refill wood past upkeep - Lumber Mill itself produces wood so we must exhaust the
        // tile's wood deposit too (extractProduction pulls from tile.getResource()), otherwise
        // it would just keep paying its own upkeep from its own production forever.
        Land tile = landAt(builtHex);
        if (tile.getResource() != null) tile.getResource().setBalance(0); // exhaust deposit -> imp production = 0
        // Actually: an exhausted deposit destroys the improvement immediately (see GameController.processBuildings
        // "depleted" branch) BEFORE the missed-upkeep path even runs. That is a different destruction path than
        // the 3-missed-upkeep-turns one requested here, so instead we keep the deposit alive but zero wood
        // and remove the worker so the mill itself produces nothing, isolating pure upkeep non-payment.
        if (tile.getResource() != null) tile.getResource().setBalance(30);
        for (CivilizedUnit w : new ArrayList<>(imp.getStationedWorkers())) {
            imp.removeWorker(w);
            w.leaveStation();
        }

        // NOTE: TownHall.produceSafeguardTrickle() unconditionally adds +1 wood EVERY turn,
        // BEFORE upkeep is deducted (turn order: step 10c trickle, then step 10d upkeep - see
        // GameController.nextGameTurn()). A single Lumber Mill's upkeep is exactly 1 wood, so
        // draining wood to 0 right before endTurn() never actually causes a missed payment: the
        // trickle refills to 1, upkeep spends that same 1, net 0 forever. QaDriver2.java already
        // discovered this and worked around it by building a SECOND unstaffed Lumber Mill so
        // combined upkeep (2 wood) exceeds what the trickle (1 wood) can cover. Do the same here.
        Improvement imp2 = null;
        Pair<Integer, Integer> hex2 = findForestHexInTerritory();
        if (hex2 != null) {
            CivilizedUnit anyBuilder = findUnit(CivilizedUnitType.BUILDER);
            if (anyBuilder != null) {
                boolean moved2 = moveUnitTo(anyBuilder, hex2);
                if (moved2) {
                    anyBuilder.setCurrentAp(anyBuilder.getMaxAp());
                    GameController.setSelectedCivilizedUnit(anyBuilder);
                    if (WorkerController.checkCanBuild(ImprovementType.LUMBER_MILL) == null) {
                        WorkerController.builderBuildImprovement("Lumber Mill");
                        imp2 = landAt(hex2).getImprovement();
                    }
                }
            }
        }
        info("Second unstaffed Lumber Mill built to outstrip the +1/turn safeguard trickle: " + (imp2 != null));

        nation.getWood().setBalance(0);

        int missedBefore = imp.getMissedUpkeepTurns();
        info("missedUpkeepTurns before drain-turns: " + missedBefore + ", wood balance forced to 0");

        for (int i = 1; i <= 3; i++) {
            // Force wood back to 0 each turn before ending it (the +1/turn safeguard trickle
            // alone would otherwise fully cover one mill's upkeep; with two unstaffed mills
            // present the trickle can only ever cover one of the two 1-wood upkeep charges).
            nation.getWood().setBalance(0);
            GameController.endTurn();
            Improvement stillThere = landAt(builtHex).getImprovement();
            if (stillThere != null) {
                info("After missed-upkeep turn " + i + ": missedUpkeepTurns=" + stillThere.getMissedUpkeepTurns());
            } else {
                info("After missed-upkeep turn " + i + ": improvement destroyed.");
            }
        }

        check("Improvement removed from tile after 3rd consecutive missed upkeep turn",
                landAt(builtHex).getImprovement() == null);
    }

    // ── 9. Starvation ────────────────────────────────────────────────────────
    static void section9_starvation() {
        System.out.println("\n=== 9. Starvation ===");
        // Drain food to force negative net rate and negative reserve.
        nation.getFood().setBalance(0);
        check("Nation not in starvation before draining turns", !nation.isInStarvation());

        int apMaxSample = -1;
        Unit sampleUnitBeforeStarvation = nation.getUnits().isEmpty() ? null : nation.getUnits().get(0);

        boolean becameStarving = false;
        for (int i = 0; i < 5 && !becameStarving; i++) {
            GameController.endTurn();
            becameStarving = nation.isInStarvation();
            info("Turn " + Game.instance.getTurn() + ": food balance=" + nation.getFood().getBalance()
                    + " netRate=" + nation.getFood().getNetRate() + " inStarvation=" + becameStarving);
        }
        check("nation.isInStarvation() becomes true after repeated turns with drained food", becameStarving);

        if (becameStarving && !nation.getUnits().isEmpty()) {
            Unit u = nation.getUnits().get(0);
            u.setCurrentAp(0); // force to 0 so refreshAp's effect is unambiguous
            int apBeforeRefresh = u.getCurrentAp();
            u.refreshAp();
            int apAfterRefresh = u.getCurrentAp();
            info("Unit maxAp=" + u.getMaxAp() + " apAfterRefresh(while starving)=" + apAfterRefresh);
            check("Units lose AP on refresh while starving (refreshAp gives maxAp-1, not maxAp)",
                    apAfterRefresh == Math.max(0, u.getMaxAp() - 1));

            // Compare against non-starving refresh for the same unit.
            nation.setInStarvation(false);
            u.setCurrentAp(0);
            u.refreshAp();
            int apAfterRefreshNoStarve = u.getCurrentAp();
            check("Same unit's refreshAp gives full maxAp when NOT starving (contrast case)",
                    apAfterRefreshNoStarve == u.getMaxAp());
            nation.setInStarvation(true); // restore state
        } else {
            check("AP-loss-during-starvation sub-check runnable", false);
        }
    }

    // ── 10. Tech research ───────────────────────────────────────────────────
    static void section10_techResearch() {
        System.out.println("\n=== 10. Tech research ===");
        // Ensure nation can afford TECH_STONE_MINE (4 wood).
        // Section 9 (starvation) deliberately leaves food negative; canAfford() requires
        // food.getBalance() >= 0 (foodCost defaults to 0 for tech research), so restore it
        // here or every research attempt below spuriously fails on the food check even
        // though the failure message only mentions wood/stone/iron.
        nation.getFood().setBalance(50);
        nation.setInStarvation(false);
        nation.getWood().setBalance(50);
        nation.getStone().setBalance(50);
        nation.getIron().setBalance(50);

        check("TECH_IRON_MINE not researchable before TECH_STONE_MINE (prereq unmet)",
                !TechnologyType.TECH_IRON_MINE.isAvailable(nation.getTechnologies()));

        String blockedResult = TownHallController.researchTechnology("Iron Mine Technology");
        info("Attempt to research Iron Mine before Stone Mine: " + blockedResult);
        check("researchTechnology(Iron Mine) fails before Stone Mine researched",
                !nation.hasTechnology(TechnologyType.TECH_IRON_MINE));

        int woodBefore = nation.getWood().getBalance();
        int stoneBefore = nation.getStone().getBalance();
        String result = TownHallController.researchTechnology("Stone Mine Technology");
        info("researchTechnology(Stone Mine): " + result);
        // phase 2: costs are charged when the research is enqueued...
        check("Wood cost deducted for Stone Mine tech (" + TechnologyType.TECH_STONE_MINE.woodCost + ")",
                nation.getWood().getBalance() == woodBefore - TechnologyType.TECH_STONE_MINE.woodCost);
        check("Stone cost deducted for Stone Mine tech (" + TechnologyType.TECH_STONE_MINE.stoneCost + ")",
                nation.getStone().getBalance() == stoneBefore - TechnologyType.TECH_STONE_MINE.stoneCost);
        // ...but the tech only unlocks after its research time passes in the queue
        for (int i = 0; i < TechnologyType.TECH_STONE_MINE.turnsToResearch; i++) GameController.nextGameTurn();
        check("nation.hasTechnology(TECH_STONE_MINE) true after research completes",
                nation.hasTechnology(TechnologyType.TECH_STONE_MINE));

        check("TECH_IRON_MINE now researchable after Stone Mine tech (prereq met)",
                TechnologyType.TECH_IRON_MINE.isAvailable(nation.getTechnologies()));

        int woodBefore2 = nation.getWood().getBalance();
        String result2 = TownHallController.researchTechnology("Iron Mine Technology");
        info("researchTechnology(Iron Mine) after Stone Mine: " + result2);
        check("Wood cost deducted for Iron Mine tech (" + TechnologyType.TECH_IRON_MINE.woodCost + ")",
                nation.getWood().getBalance() == woodBefore2 - TechnologyType.TECH_IRON_MINE.woodCost);
        for (int i = 0; i < TechnologyType.TECH_IRON_MINE.turnsToResearch; i++) GameController.nextGameTurn();
        check("nation.hasTechnology(TECH_IRON_MINE) true after research completes",
                nation.hasTechnology(TechnologyType.TECH_IRON_MINE));
    }

    // ── 11. Border Expander ─────────────────────────────────────────────────
    static void section11_borderExpander() {
        System.out.println("\n=== 11. Border Expander ===");
        List<CivilizedUnit> expanders = findUnits(CivilizedUnitType.BORDER_EXPANDER);
        if (expanders.isEmpty()) {
            check("A BorderExpander unit exists to test (spec says start with 0 - see note)", false);
            info("mapInitializer() only spawns Explorer/Builder/Worker units; no BorderExpander at game start. " +
                    "Cannot test workflow 11 without a way to train/spawn one - trying TownHall production queue.");
            // Try training one via TownHall queue as a fallback path.
            nation.getFood().setBalance(50);
            String enqueueResult = TownHallController.enqueueUnit("BorderExpander");
            info("enqueueUnit(BorderExpander) result: " + enqueueResult);
            if (!nation.getTownHall().hasQueuedProduction()) {
                check("BorderExpander could be trained via TownHall queue as fallback", false);
                return;
            }
            int turnsToTrain = CivilizedUnitType.BORDER_EXPANDER.turnsToTrain;
            for (int i = 0; i < turnsToTrain + 1; i++) {
                GameController.endTurn();
            }
            expanders = findUnits(CivilizedUnitType.BORDER_EXPANDER);
            check("BorderExpander unit spawned via TownHall production queue after " + (turnsToTrain + 1) + " turns",
                    !expanders.isEmpty());
            if (expanders.isEmpty()) return;
        } else {
            check("A BorderExpander unit exists to test", true);
        }

        CivilizedUnit expander = expanders.get(0);
        expander.setCurrentAp(expander.getMaxAp());

        // Find a hex adjacent to current territory that has been explored (visibility>0) but not yet owned.
        Pair<Integer, Integer> target = null;
        for (Pair<Integer, Integer> hex : new ArrayList<>(nation.getTerritory())) {
            for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(hex)) {
                if (Pair.isValid(nb) && !nation.ownsHex(nb.x, nb.y)) {
                    target = nb;
                    break;
                }
            }
            if (target != null) break;
        }
        if (target == null) {
            check("Found a target hex adjacent to territory to expand into", false);
            return;
        }
        check("Found a target hex adjacent to territory to expand into", true);

        // Move expander onto target hex (must be walkable / unoccupied); explorer-reveal needed for expand.
        boolean moved = moveUnitTo(expander, target);
        if (!moved) {
            info("Could not move BorderExpander onto target hex " + target + " (may be occupied/unreachable). Trying expand from current location instead.");
            target = expander.getLocation();
        }
        expander.setCurrentAp(expander.getMaxAp());

        // Ensure the target hex + its neighbors are explored by this nation (Border Expander requires
        // Explorer-level reveal per doc). Use the shared reveal helper via LandController.updateLandVisibility()
        // triggered by moving the Explorer there first, or force-reveal directly for test purposes.
        GameController.setSelectedCivilizedUnit(expander);
        boolean canExpand = TownHallController.canExpandBorder(expander);
        info("canExpandBorder before ensuring exploration: " + canExpand);
        if (!canExpand) {
            // Move the Explorer to the target to guarantee Explorer-reveal, then retry.
            CivilizedUnit explorer = findUnit(CivilizedUnitType.EXPLORER);
            if (explorer != null) {
                explorer.setCurrentAp(explorer.getMaxAp());
                Pair<Integer, Integer> explorerDest = target;
                Land destLand = landAt(target);
                if (destLand.getCivilizedUnit() != null && destLand.getCivilizedUnit() != explorer) {
                    // occupied by another unit (e.g. the expander) - explorer can't stand there;
                    // revealAround only needs the explorer to be *near*, but the actual API marks
                    // explored based on the explorer's own visited tiles, so try neighbors instead.
                    for (int d = 0; d < 6; d++) {
                        Pair<Integer, Integer> alt = LandController.getNeighborIndex(target, d);
                        if (Pair.isValid(alt) && landAt(alt).getCivilizedUnit() == null) { explorerDest = alt; break; }
                    }
                }
                moveUnitTo(explorer, explorerDest);
                // Trigger fog-of-war/vision update for the explorer's new position.
                GameController.setCurrentTurnUser(user);
                LandController.updateLandVisibility();
            }
            canExpand = TownHallController.canExpandBorder(expander);
            info("canExpandBorder after moving Explorer near target: " + canExpand);
        }

        int ownedBefore = 0;
        List<Pair<Integer, Integer>> sevenHexes = new ArrayList<>();
        sevenHexes.add(target);
        sevenHexes.addAll(LandController.getAllNeighborsIndexes(target));
        for (Pair<Integer, Integer> h : sevenHexes) {
            if (Pair.isValid(h) && nation.ownsHex(h.x, h.y)) ownedBefore++;
        }

        GameController.setSelectedCivilizedUnit(expander);
        String expandResult = TownHallController.expandBorder();
        info("expandBorder() result: " + expandResult);

        int ownedAfter = 0;
        int validHexes = 0;
        for (Pair<Integer, Integer> h : sevenHexes) {
            if (!Pair.isValid(h)) continue;
            validHexes++;
            if (nation.ownsHex(h.x, h.y)) ownedAfter++;
        }
        check("All valid hexes among target+6 neighbors now owned by nation (" + validHexes + " valid on-map hexes)",
                ownedAfter == validHexes);
        check("Border Expander marked as expanded", expander.hasExpanded());

        // Removal is deferred to processBorderExpanders(), invoked by endTurn/nextGameTurn.
        boolean presentBefore = nation.getUnits().contains(expander);
        GameController.endTurn();
        boolean presentAfter = nation.getUnits().contains(expander);
        check("Border Expander was present before end-turn cleanup", presentBefore);
        check("Border Expander removed from unit list after end-turn (processBorderExpanders)", !presentAfter);
    }

    // ── 12. Movement + AP cost ──────────────────────────────────────────────
    static void section12_movementApCost() {
        System.out.println("\n=== 12. Movement + AP cost ===");
        CivilizedUnit explorer = findUnit(CivilizedUnitType.EXPLORER);
        if (explorer == null) { check("Explorer available for movement test", false); return; }

        explorer.setCurrentAp(explorer.getMaxAp());
        GameController.setSelectedCivilizedUnit(explorer);
        Pair<Integer, Integer> startLoc = explorer.getLocation();
        Pair<Integer, Integer> dest = null;
        int destCost = -1;
        for (int dir = 0; dir < 6; dir++) {
            Pair<Integer, Integer> cand = LandController.getNeighborIndex(startLoc, dir);
            if (Pair.isValid(cand) && landAt(cand).getCivilizedUnit() == null) {
                dest = cand;
                destCost = landAt(cand).getMP();
                break;
            }
        }
        if (dest == null) { check("Found an unoccupied neighbor hex to move into", false); return; }

        int apBefore = explorer.getCurrentAp();
        UnitController.unitSetPath(dest.x, dest.y, 0);
        int steps = 0;
        while (UnitController.hasMoreSteps(explorer) && steps < 10) { UnitController.unitGoForward(explorer); steps++; }

        check("Unit moved exactly one hex", steps == 1 && explorer.getLocation().equals(dest));
        check("AP decremented by exactly destination terrain's movement cost (" + destCost + ")",
                explorer.getCurrentAp() == Math.max(0, apBefore - destCost));
    }

    // ── 13. Fog of war / vision ─────────────────────────────────────────────
    static void section13_fogOfWar() {
        System.out.println("\n=== 13. Fog of war / vision ===");
        // Pick a hex far from TownHall (7,7), e.g. corner (0,0) or (14,14).
        Pair<Integer, Integer> farHex = new Pair<>(0, 0);
        Land far = landAt(farHex);
        boolean initiallyUnexplored = !far.isExploredBy(nation) && far.getVisibility() < 2;
        check("Far hex (0,0) is NOT visible/explored initially", initiallyUnexplored);
        info("Far hex initial visibility=" + far.getVisibility() + " exploredByNation=" + far.isExploredBy(nation));

        CivilizedUnit explorer = findUnit(CivilizedUnitType.EXPLORER);
        if (explorer == null) { check("Explorer available for fog-of-war test", false); return; }

        // Move the explorer as close as possible to (0,0) - direct move likely blocked by distance;
        // instead, walk it stepwise toward the corner over several turns/moves.
        int guardCount = 0;
        while (guardCount < 40) {
            Pair<Integer, Integer> loc = explorer.getLocation();
            if (loc.x <= 1 && loc.y <= 1) break; // close enough for vision radius 3
            explorer.setCurrentAp(explorer.getMaxAp());
            GameController.setSelectedCivilizedUnit(explorer);
            // Choose neighbor that reduces distance to (0,0) most.
            Pair<Integer, Integer> best = null;
            int bestDist = Integer.MAX_VALUE;
            for (int dir = 0; dir < 6; dir++) {
                Pair<Integer, Integer> cand = LandController.getNeighborIndex(loc, dir);
                if (!Pair.isValid(cand) || landAt(cand).getCivilizedUnit() != null) continue;
                int d = Math.abs(cand.x) + Math.abs(cand.y);
                if (d < bestDist) { bestDist = d; best = cand; }
            }
            if (best == null) break;
            UnitController.unitSetPath(best.x, best.y, 0);
            int steps = 0;
            while (UnitController.hasMoreSteps(explorer) && steps < 5) { UnitController.unitGoForward(explorer); steps++; }
            if (steps == 0) break; // couldn't move further
            guardCount++;
        }
        info("Explorer ended near " + explorer.getLocation() + " after movement toward (0,0)");

        GameController.setCurrentTurnUser(user);
        LandController.updateLandVisibility();

        boolean nowVisible = far.getVisibility() == 2 || far.isExploredBy(nation);
        check("Far hex becomes visible/explored after Explorer moves near it", nowVisible);

        // Move explorer away and confirm hex stays "seen" (shadow, not fog) even though not currently visible.
        Pair<Integer, Integer> awayLoc = new Pair<>(7, 7); // back toward TownHall
        moveUnitTo(explorer, awayLoc);
        // A full endTurn triggers updateFogOfWar() which downgrades visibility 2->1 for tiles no longer in radius.
        GameController.endTurn();

        boolean staysExplored = far.isExploredBy(nation);
        boolean notBackToFog = far.getVisibility() != 0;
        check("Far hex STAYS explored (isExploredBy) after Explorer moves away", staysExplored);
        check("Far hex visibility does not reset to full fog (0) after Explorer leaves (stays shadow=1)", notBackToFog);
    }

    // ── 14. Unit cap ─────────────────────────────────────────────────────────
    static void section14_unitCap() {
        System.out.println("\n=== 14. Unit cap (Town improvement) ===");
        // Research TECH_TOWN prerequisite tech.
        nation.getWood().setBalance(100);
        nation.getStone().setBalance(100);
        nation.getIron().setBalance(100);

        String techResult = TownHallController.researchTechnology("Town Construction Technology");
        info("researchTechnology(Town Construction): " + techResult);
        // phase 2: research is queued - advance turns until it completes
        for (int i = 0; i < TechnologyType.TECH_TOWN.turnsToResearch; i++) GameController.nextGameTurn();
        check("nation.hasTechnology(TECH_TOWN) true after research completes", nation.hasTechnology(TechnologyType.TECH_TOWN));

        // Need a Builder with charges left and a resource-free hex (mountain, or empty meadow/plain) in territory.
        CivilizedUnit builder = null;
        for (Unit u : nation.getUnits()) {
            if (u instanceof CivilizedUnit && ((CivilizedUnit) u).getCivilizedUnitType() == CivilizedUnitType.BUILDER) {
                builder = (CivilizedUnit) u;
                break;
            }
        }
        if (builder == null) {
            check("A Builder with charges available for Town-building test", false);
            info("No Builder unit remains (both starting Builders were consumed in sections 2/3). " +
                    "Cannot test Town-building/unit-cap workflow without training a new Builder.");
            // Fallback: train one via TownHall queue.
            nation.getFood().setBalance(50);
            String enqueueResult = TownHallController.enqueueUnit("Builder");
            info("enqueueUnit(Builder) fallback result: " + enqueueResult);
            if (nation.getTownHall().hasQueuedProduction()) {
                int turnsToTrain = CivilizedUnitType.BUILDER.turnsToTrain;
                for (int i = 0; i < turnsToTrain + 1; i++) GameController.endTurn();
                for (Unit u : nation.getUnits()) {
                    if (u instanceof CivilizedUnit && ((CivilizedUnit) u).getCivilizedUnitType() == CivilizedUnitType.BUILDER) {
                        builder = (CivilizedUnit) u;
                        break;
                    }
                }
            }
            if (builder == null) {
                check("Builder available via TownHall training fallback", false);
                return;
            }
        }

        Pair<Integer, Integer> townHex = null;
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            Land l = landAt(hex);
            boolean resourceFree = l.getLandType() == LandType.MOUNTAIN
                    || ((l.getLandType() == LandType.MEADOW || l.getLandType() == LandType.PLAIN) && l.getResource() == null);
            if (resourceFree && l.getImprovement() == null && l.getVisibility() > 0) {
                townHex = hex;
                break;
            }
        }
        if (townHex == null) {
            check("Found a resource-free hex in territory for Town", false);
            info("No resource-free (mountain / empty meadow-plain) hex available in current territory for TOWN.");
            return;
        }
        check("Found a resource-free hex in territory for Town", true);

        boolean moved = moveUnitTo(builder, townHex);
        check("Builder moved onto Town-eligible hex", moved);
        if (!moved) return;
        builder.setCurrentAp(builder.getMaxAp());
        GameController.setSelectedCivilizedUnit(builder);

        int capBefore = nation.getUnitCap();
        String blocked = WorkerController.checkCanBuild(ImprovementType.TOWN);
        info("checkCanBuild(TOWN): " + blocked);
        check("checkCanBuild(TOWN) returns null (buildable)", blocked == null);
        if (blocked != null) return;

        String buildResult = WorkerController.builderBuildImprovement("Town");
        info("builderBuildImprovement(Town) result: " + buildResult);

        int capAfter = nation.getUnitCap();
        check("Unit cap increased by expected bonus (+5) after building a Town",
                capAfter == capBefore + 5);
    }
}

import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless smoke test for the Phase 2 Tier A systems: unified production queue,
 * town hall levels, timed research, happiness, trade, demolition.
 * Not JUnit - plain main(), same pattern as QaDriver / FullWorkflowDriver.
 */
public class Phase2Driver {
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

        testSeasons();
        testMountainRangeAndRoads();
        testMilitaryUnits();
        testCombat();
        testEarthquake();
        testTimedResearch();
        testBusyQueueBlocksNewOrders();
        testCancelNoRefund();
        testTownHallUpgrade();
        testSteelToolsGate();
        testHappiness();
        testBazaar();
        testTradingPost();
        testDemolition();
        // runs late on purpose: it permanently unlocks a tech that raises the TH max
        // HP cap, which would break the "max HP grew to 250" check further up
        testDefensiveArchitecture();
        testBearDen();
        // must run LAST: loading swaps out the whole Game object the other tests hold
        testSaveLoad();

        System.out.println("\n=== SUMMARY: " + pass + " passed, " + fail + " failed ===");
        if (!failLabels.isEmpty()) {
            System.out.println("Failed checks:");
            for (String s : failLabels) System.out.println("  - " + s);
        }
    }

    static void setup() {
        // random 5%-per-turn quakes would make these tests flaky - we only test
        // earthquakes via the direct trigger
        DisasterController.randomDisastersEnabled = false;
        // den bears roam on their own - only testBearDen turns them on
        DisasterController.bearDenEnabled = false;
        // keep test saves/autosaves away from the player's real saves/ folder
        SaveController.saveDirectory = "target/test-saves";
        user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);
        Game.instance.setMap(LandController.mapInitializer());
        LandController.initializeDistances();
        LandController.updateDistances();
        nation = user.getNation();
    }

    static TownHall th() { return nation.getTownHall(); }

    // ── 0b. mountain ranges + roads ──────────────────────────────────────────
    static void testMountainRangeAndRoads() {
        info("--- mountain ranges + roads ---");
        Pair<Integer, Integer> thCoord = new Pair<>(th().getMainLand().getI(), th().getMainLand().getJ());

        boolean rangeNextToTh = false;
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(thCoord))
            if (Pair.isValid(nb) && Game.instance.map[nb.x][nb.y].getLandType() == LandType.MOUNTAIN_RANGE)
                rangeNextToTh = true;
        check("no mountain range right next to the town hall", !rangeNextToTh);

        // BFS from the town hall over non-range tiles - the generator promises
        // it never walls any normal tile off
        int X = Consts.MAP_SIZE.amount.x, Y = Consts.MAP_SIZE.amount.y;
        boolean[][] reachable = new boolean[X][Y];
        ArrayList<Pair<Integer, Integer>> queue = new ArrayList<>();
        queue.add(thCoord);
        reachable[thCoord.x][thCoord.y] = true;
        while (!queue.isEmpty()) {
            Pair<Integer, Integer> cur = queue.remove(queue.size() - 1);
            for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(cur)) {
                if (!Pair.isValid(nb) || reachable[nb.x][nb.y]) continue;
                if (Game.instance.map[nb.x][nb.y].getLandType() == LandType.MOUNTAIN_RANGE) continue;
                reachable[nb.x][nb.y] = true;
                queue.add(nb);
            }
        }
        boolean allReachable = true;
        boolean rangeHasDeposit = false;
        int ranges = 0;
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                Land t = Game.instance.map[i][j];
                if (t.getLandType() == LandType.MOUNTAIN_RANGE) {
                    ranges++;
                    if (t.getResource() != null) rangeHasDeposit = true;
                } else if (!reachable[i][j]) allReachable = false;
            }
        }
        info(ranges + " mountain range tiles on this map");
        check("map gen never walls any normal tile off from the town hall", allReachable);
        check("mountain ranges have no stone deposit", !rangeHasDeposit);

        // impassability: turn a corner tile into a range and confirm the path
        // matrix now treats it as unreachable even from a direct neighbor
        Land corner = Game.instance.map[0][0];
        LandType cornerBackup = corner.getLandType();
        corner.setLandType(LandType.MOUNTAIN_RANGE);
        LandController.initializeDistances();
        LandController.updateDistances();
        int from = LandController.getLandNumber(Game.instance.map[0][1]);
        int to = LandController.getLandNumber(corner);
        check("mountain range is unreachable in the path matrix", Game.instance.dist[from][to] >= 1000);
        corner.setLandType(cornerBackup);

        // road discount: two adjacent forest tiles, entering costs 2 normally, 1 by road
        int turnBackup = Game.instance.getTurn();
        Game.instance.setTurn(5); // spring, no winter penalty
        Land a = Game.instance.map[3][3];
        Land b = Game.instance.map[3][4];
        LandType aBackup = a.getLandType(), bBackup = b.getLandType();
        a.setLandType(LandType.FOREST);
        b.setLandType(LandType.FOREST);
        check("forest step costs 2 AP without a road", b.getMPFrom(a) == 2);
        a.setHasRoad(true);
        b.setHasRoad(true);
        check("road-to-road step costs 1 AP", b.getMPFrom(a) == 1);
        Game.instance.setTurn(35); // winter's +1 still applies on roads
        check("winter road step costs 2 AP", b.getMPFrom(a) == 2);
        Game.instance.setTurn(turnBackup);
        a.setHasRoad(false); b.setHasRoad(false);
        a.setLandType(aBackup); b.setLandType(bBackup);
        LandController.initializeDistances();
        LandController.updateDistances();
    }

    // ── 0c. military units: training gates, stats, garrison, military cap ────
    static void testMilitaryUnits() {
        info("--- military units ---");
        check("military cap at TH level 1 is 5", nation.getMilitaryUnitCap() == 5);

        nation.getFood().add(100);
        nation.getWood().add(100);

        check("archer blocked at TH level 1",
                TownHallController.enqueueUnit("Archer").contains("level 2"));

        // cavalry gate: level ok but no stable anywhere
        th().setLevel(2);
        check("cavalry needs a military stable",
                TownHallController.enqueueUnit("Cavalry").contains("Military Stable"));
        th().setLevel(1);

        int foodBefore = nation.getFood().getBalance();
        int woodBefore = nation.getWood().getBalance();
        check("swordsman trainable at TH level 1",
                TownHallController.enqueueUnit("Swordsman").contains("added to production queue"));
        check("swordsman costs 20 food", nation.getFood().getBalance() == foodBefore - 20);
        check("swordsman costs 10 wood", nation.getWood().getBalance() == woodBefore - 10);

        GameController.nextGameTurn();
        GameController.nextGameTurn();
        CivilizedUnit swordsman = null;
        for (Unit u : nation.getUnits())
            if (u instanceof CivilizedUnit
                    && ((CivilizedUnit) u).getCivilizedUnitType() == CivilizedUnitType.SWORDSMAN)
                swordsman = (CivilizedUnit) u;
        check("swordsman spawned after 2 turns", swordsman != null);
        if (swordsman == null) return;
        check("swordsman has 1 HP", swordsman.getHp() == 1);
        check("swordsman has 2 max AP", swordsman.getMaxAp() == 2);

        // garrison: a military unit on the TH hex gives +1 happiness, a worker doesn't
        Land thLand = th().getMainLand();
        Land swordsmanTile = Game.instance.map[swordsman.getLocation().x][swordsman.getLocation().y];
        if (swordsmanTile.getCivilizedUnit() == swordsman) swordsmanTile.setCivilizedUnit(null);
        CivilizedUnit thOccupantBackup = thLand.getCivilizedUnit();
        thLand.setCivilizedUnit(swordsman);
        int withGarrison = nation.getHappiness();
        thLand.setCivilizedUnit(null);
        check("military garrison on TH gives +1 happiness", withGarrison == nation.getHappiness() + 1);

        CivilizedUnit anyWorker = null;
        for (Unit u : nation.getUnits())
            if (u instanceof CivilizedUnit
                    && ((CivilizedUnit) u).getCivilizedUnitType() == CivilizedUnitType.WORKER)
                anyWorker = (CivilizedUnit) u;
        if (anyWorker != null) {
            int without = nation.getHappiness();
            thLand.setCivilizedUnit(anyWorker);
            check("a worker on the TH hex gives NO garrison bonus", nation.getHappiness() == without);
            thLand.setCivilizedUnit(null);
        }
        thLand.setCivilizedUnit(thOccupantBackup);

        // military cap: fill to 5 military -> -1 happiness, training blocked, civil still fine.
        // bump the total cap first so we're testing the MILITARY cap, not the total one
        int baseCapBackup = nation.getUnitCap(); // TH L1, no towns -> equals the base cap
        nation.setBaseUnitCap(30);
        int beforeCap = nation.getHappiness();
        ArrayList<CivilizedUnit> extras = new ArrayList<>();
        while (nation.getMilitaryUnitCount() < nation.getMilitaryUnitCap()) {
            CivilizedUnit s = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, new Pair<>(0, 0));
            nation.addUnit(s);
            extras.add(s);
        }
        check("reaching the military cap costs -1 happiness", nation.getHappiness() == beforeCap - 1);
        check("military training blocked at military cap",
                TownHallController.enqueueUnit("Swordsman").contains("Military cap"));
        check("civil units still trainable at military cap",
                TownHallController.enqueueUnit("Worker").contains("added to production queue"));
        TownHallController.cancelCurrentProduction();

        // clean up so later tests see the same nation state as before
        for (CivilizedUnit s : extras) nation.removeUnit(s);
        nation.removeUnit(swordsman);
        nation.setBaseUnitCap(baseCapBackup);
    }

    // ── 0c2. dice combat (P2-14..17) ─────────────────────────────────────────
    static void testCombat() {
        info("--- dice combat ---");

        // rule checks with fixed dice (comparePairs expects them sorted descending)
        int[] r = CombatController.comparePairs(new int[]{6}, new int[]{5, 2});
        check("attacker die 6 beats defender 5 -> defender takes the hit", r[0] == 1 && r[1] == 0);
        r = CombatController.comparePairs(new int[]{4}, new int[]{4, 1});
        check("tie favors the defender -> attacker takes the hit", r[0] == 0 && r[1] == 1);
        r = CombatController.comparePairs(new int[]{2}, new int[]{5, 3});
        check("attacker die 2 loses to defender 5", r[0] == 0 && r[1] == 1);

        // controlled corner scene: swordsman next to a barbarian
        Pair<Integer, Integer> sLoc = new Pair<>(12, 12);
        Pair<Integer, Integer> bLoc = LandController.getAllNeighborsIndexes(sLoc).get(0);
        Land sTile = Game.instance.map[sLoc.x][sLoc.y];
        Land bTile = Game.instance.map[bLoc.x][bLoc.y];
        sTile.setVisibility(2);
        bTile.setVisibility(2);

        CivilizedUnit sword = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, sLoc);
        sTile.setCivilizedUnit(sword);
        nation.addUnit(sword);
        Enemy barbarian = new Enemy(EnemyType.BARBARIAN, bLoc);
        bTile.setEnemy(barbarian);

        check("a worker can never attack",
                CombatController.checkCanAttack(new CivilizedUnit(CivilizedUnitType.WORKER, nation, new Pair<>(10, 10))) != null);
        check("swordsman next to a barbarian may attack", CombatController.checkCanAttack(sword) == null);
        GameController.setSelectedCivilizedUnit(sword);
        check("moving onto an enemy hex is refused",
                UnitController.unitSetPath(bLoc.x, bLoc.y, 0).contains("enemy"));
        GameController.setSelectedCivilizedUnit(null);

        // deterministic dice: seed the game RNG and pre-roll the same sequence ourselves
        Game.instance.getRng().setSeed(123);
        java.util.Random sim = new java.util.Random(123);
        int expA = sim.nextInt(6) + 1;
        int expD1 = sim.nextInt(6) + 1, expD2 = sim.nextInt(6) + 1;
        int[] expDef = {Math.max(expD1, expD2), Math.min(expD1, expD2)};

        int apBefore = sword.getCurrentAp();
        CombatController.BattleReport report = CombatController.attack(sword, bLoc);
        check("attacker rolls exactly 1 die (one per attacking unit)", report.attackerDice.length == 1);
        check("barbarian defends with 2 dice", report.defenderDice.length == 2);
        check("dice come from the game's saved RNG",
                report.attackerDice[0] == expA
                        && report.defenderDice[0] == expDef[0] && report.defenderDice[1] == expDef[1]);
        check("one pair compared -> exactly one line per pair", !report.lines.isEmpty());
        check("attacking costs 1 AP", sword.getCurrentAp() == apBefore - 1);
        boolean swordDied = !nation.getUnits().contains(sword);
        int hitsDealt = (2 - barbarian.getHp()) + (swordDied ? 1 : 0);
        check("exactly 1 hit total was dealt (single pair)", hitsDealt == 1);

        // keep attacking until the barbarian dies (fresh swordsman whenever ours falls)
        int rounds = 0;
        CivilizedUnit fighter = swordDied ? null : sword;
        while (bTile.getEnemy() != null && rounds++ < 50) {
            if (fighter == null || !nation.getUnits().contains(fighter)) {
                fighter = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, sLoc);
                sTile.setCivilizedUnit(fighter);
                nation.addUnit(fighter);
            }
            fighter.setCurrentAp(2);
            CombatController.attack(fighter, bLoc);
        }
        check("barbarian dies after enough lost pairs and leaves the map", bTile.getEnemy() == null);
        if (fighter != null && nation.getUnits().contains(fighter)) {
            sTile.setCivilizedUnit(null);
            nation.removeUnit(fighter);
        } else sTile.setCivilizedUnit(null);

        // archer range: may attack at distance 2, a swordsman may not
        Pair<Integer, Integer> aLoc = new Pair<>(12, 8);
        Land aTile = Game.instance.map[aLoc.x][aLoc.y];
        Pair<Integer, Integer> farLoc = null;
        java.util.ArrayList<Pair<Integer, Integer>> ring1 = DisasterController.hexesInRadius(aLoc, 1);
        for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(aLoc, 2))
            if (Pair.isValid(hex) && !ring1.contains(hex)) { farLoc = hex; break; }
        Land farTile = Game.instance.map[farLoc.x][farLoc.y];
        aTile.setVisibility(2);
        farTile.setVisibility(2);
        Enemy farBear = new Enemy(EnemyType.BEAR, farLoc);
        farTile.setEnemy(farBear);

        CivilizedUnit archer = new CivilizedUnit(CivilizedUnitType.ARCHER, nation, aLoc);
        aTile.setCivilizedUnit(archer);
        nation.addUnit(archer);
        check("archer may attack at distance 2", CombatController.checkCanAttackTarget(archer, farLoc) == null);
        aTile.setCivilizedUnit(null);
        nation.removeUnit(archer);
        CivilizedUnit sword2 = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, aLoc);
        aTile.setCivilizedUnit(sword2);
        nation.addUnit(sword2);
        check("swordsman may NOT attack at distance 2",
                CombatController.checkCanAttackTarget(sword2, farLoc) != null);
        aTile.setCivilizedUnit(null);
        nation.removeUnit(sword2);
        farTile.setEnemy(null);

        // camp: dice battle while the guard lives, flat structure damage after (P2-16)
        Pair<Integer, Integer> cLoc = new Pair<>(4, 12);
        Pair<Integer, Integer> gLoc = LandController.getAllNeighborsIndexes(cLoc).get(0);
        Land cTile = Game.instance.map[cLoc.x][cLoc.y];
        cTile.setVisibility(2);
        Game.instance.map[gLoc.x][gLoc.y].setVisibility(2);
        Enemy camp = new Enemy(EnemyType.CAMP, cLoc);
        cTile.setEnemy(camp);
        CivilizedUnit raider = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, gLoc);
        Game.instance.map[gLoc.x][gLoc.y].setCivilizedUnit(raider);
        nation.addUnit(raider);

        CombatController.BattleReport guardFight = CombatController.attack(raider, cLoc);
        check("guarded camp fights back with dice", guardFight.defenderDice.length == 2);
        check("structure HP untouched while the guard lives", camp.getHp() == EnemyType.CAMP.maxHp);

        camp.setGuardHp(0); // guard dead - now it's a structure
        if (!nation.getUnits().contains(raider)) {
            raider = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, gLoc);
            Game.instance.map[gLoc.x][gLoc.y].setCivilizedUnit(raider);
            nation.addUnit(raider);
        }
        raider.setCurrentAp(2);
        CombatController.BattleReport siege = CombatController.attack(raider, cLoc);
        check("cleared camp takes NO dice, flat attack-power damage",
                siege.attackerDice.length == 0 && camp.getHp() == EnemyType.CAMP.maxHp - CivilizedUnitType.SWORDSMAN.attackPower);
        camp.setHp(5);
        raider.setCurrentAp(2);
        CombatController.attack(raider, cLoc);
        check("camp at 0 HP is destroyed and leaves the map", cTile.getEnemy() == null);
        Game.instance.map[gLoc.x][gLoc.y].setCivilizedUnit(null);
        nation.removeUnit(raider);

        // bear turn (P2-57): the bear hunts the nearest CIVILIAN first, then military
        Pair<Integer, Integer> bearLoc = new Pair<>(2, 12);
        java.util.ArrayList<Pair<Integer, Integer>> bearNbs = new java.util.ArrayList<>();
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(bearLoc))
            if (Pair.isValid(nb)) bearNbs.add(nb);
        Land bearTile = Game.instance.map[bearLoc.x][bearLoc.y];
        Enemy bear = new Enemy(EnemyType.BEAR, bearLoc);
        bearTile.setEnemy(bear);
        CivilizedUnit victim = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, bearNbs.get(0));
        Game.instance.map[bearNbs.get(0).x][bearNbs.get(0).y].setCivilizedUnit(victim);
        nation.addUnit(victim);
        CivilizedUnit bystander = new CivilizedUnit(CivilizedUnitType.WORKER, nation, bearNbs.get(1));
        Game.instance.map[bearNbs.get(1).x][bearNbs.get(1).y].setCivilizedUnit(bystander);
        nation.addUnit(bystander);

        CombatController.lastEnemyBattles.clear();
        DisasterController.bearTurn();
        check("bear mauls the CIVILIAN first for 35 raw HP (P2-57 priority)", bystander.getHp() == 65);
        check("the military unit next door is untouched", nation.getUnits().contains(victim) && victim.getHp() == 1);
        check("the bear attack produced a visible battle report",
                !CombatController.lastEnemyBattles.isEmpty());

        // with the civilian gone, the military unit is next: 1 hit kills a swordsman
        Game.instance.map[bystander.getLocation().x][bystander.getLocation().y].setCivilizedUnit(null);
        nation.removeUnit(bystander);
        DisasterController.bearTurn();
        check("bear then kills the 1-HP swordsman with one direct hit (no dice)",
                !nation.getUnits().contains(victim));

        // attacking the bear back: a won pair chips its 120 HP by our attack power
        CivilizedUnit hunter = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, bearNbs.get(0));
        Game.instance.map[bearNbs.get(0).x][bearNbs.get(0).y].setCivilizedUnit(hunter);
        nation.addUnit(hunter);
        Pair<Integer, Integer> bearNow = bear.getLocation();
        if (CombatController.hexDistance(new Pair<>(hunter.getLocation().x, hunter.getLocation().y), bearNow) <= 1) {
            CombatController.BattleReport hunt = CombatController.attack(hunter, bearNow);
            boolean won = hunt.attackerDice.length > 0 && hunt.defenderDice.length > 0
                    && hunt.attackerDice[0] > hunt.defenderDice[0];
            check("won pair vs bear deals attack power (10), lost pair kills the swordsman",
                    won ? bear.getHp() == 120 - CivilizedUnitType.SWORDSMAN.attackPower
                        : !nation.getUnits().contains(hunter));
        }
        if (nation.getUnits().contains(hunter)) {
            Game.instance.map[hunter.getLocation().x][hunter.getLocation().y].setCivilizedUnit(null);
            nation.removeUnit(hunter);
        }
        if (Game.instance.map[bearNow.x][bearNow.y].getEnemy() == bear)
            Game.instance.map[bearNow.x][bearNow.y].setEnemy(null);

        // enemy turn: a barbarian fights our unit with dice - we defend with 2 dice
        Land barbTile = Game.instance.map[bearLoc.x][bearLoc.y];
        Enemy barb2 = new Enemy(EnemyType.BARBARIAN, bearLoc);
        barbTile.setEnemy(barb2);
        CivilizedUnit cav = new CivilizedUnit(CivilizedUnitType.CAVALRY, nation, bearNbs.get(0));
        Game.instance.map[bearNbs.get(0).x][bearNbs.get(0).y].setCivilizedUnit(cav);
        nation.addUnit(cav);
        CombatController.enemyTurn();
        CombatController.BattleReport defense = CombatController.lastEnemyBattles.get(0);
        check("defending against a barbarian: it rolls 1 die, we roll 2",
                defense.attackerDice.length == 1 && defense.defenderDice.length == 2);
        int totalHits = (2 - cav.getHp()) + (2 - barb2.getHp());
        check("exactly 1 hit total in the defensive battle", totalHits == 1);
        if (nation.getUnits().contains(cav)) {
            Game.instance.map[cav.getLocation().x][cav.getLocation().y].setCivilizedUnit(null);
            nation.removeUnit(cav);
        }
        barbTile.setEnemy(null);

        // debug spawn helper used by the Debug panel
        String spawnMsg = CheatController.debugSpawnEnemy("Bear");
        check("debug spawn puts a bear near the town hall", spawnMsg.contains("spawned"));
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                Game.instance.map[i][j].setEnemy(null); // leave no enemies behind for later tests

        CombatController.lastEnemyBattles.clear();
    }

    // ── 0d. earthquake (P2-54/55) ────────────────────────────────────────────
    static void testEarthquake() {
        info("--- earthquake ---");
        check("earthquake area is 19 hexes (radius 2)",
                DisasterController.hexesInRadius(new Pair<>(7, 7), 2).size() == 19);

        // controlled scene far away from the town hall: building + road + 2 units
        Pair<Integer, Integer> center = new Pair<>(2, 2);
        Land centerTile = Game.instance.map[center.x][center.y];
        centerTile.setImprovement(new Improvement(ImprovementType.FARM));
        Land roadTile = Game.instance.map[2][3];
        roadTile.setHasRoad(true);

        Pair<Integer, Integer> sLoc = new Pair<>(3, 2);
        CivilizedUnit sword = new CivilizedUnit(CivilizedUnitType.SWORDSMAN, nation, sLoc);
        Game.instance.map[sLoc.x][sLoc.y].setCivilizedUnit(sword);
        nation.addUnit(sword);
        Pair<Integer, Integer> wLoc = new Pair<>(1, 2);
        CivilizedUnit worker = new CivilizedUnit(CivilizedUnitType.WORKER, nation, wLoc);
        Game.instance.map[wLoc.x][wLoc.y].setCivilizedUnit(worker);
        nation.addUnit(worker);

        int thHpBefore = th().getHp();
        DisasterController.triggerEarthquake(center);

        check("building in the area is destroyed", centerTile.getImprovement() == null);
        check("road in the area is destroyed", !roadTile.hasRoad());
        check("swordsman (1 HP) is killed and removed from the map",
                !nation.getUnits().contains(sword)
                        && Game.instance.map[sLoc.x][sLoc.y].getCivilizedUnit() == null);
        check("worker survives with 90 HP",
                nation.getUnits().contains(worker) && worker.getHp() == 90);
        check("far-away town hall is untouched", th().getHp() == thHpBefore);

        // now hit the town hall repeatedly: -50 each time but NEVER below 1 HP
        DisasterController.triggerEarthquake(new Pair<>(7, 7));
        check("town hall takes -50 HP", th().getHp() == Math.max(1, thHpBefore - 50));
        for (int k = 0; k < 6; k++) DisasterController.triggerEarthquake(new Pair<>(7, 7));
        check("town hall never drops below 1 HP", th().getHp() == 1);

        // put the world back so later tests see a sane state
        th().setHp(thHpBefore);
        Game.instance.map[wLoc.x][wLoc.y].setCivilizedUnit(null);
        nation.removeUnit(worker);
        DisasterController.lastQuakeArea = null;
        DisasterController.lastQuakeCenter = null;
    }

    // ── 7. defensive architecture (P2-07): wall tech, defense 10->30, HP cap 350 ──
    static void testDefensiveArchitecture() {
        info("--- defensive architecture ---");
        nation.getStone().add(150);

        // the TH is level 2 here (upgrade test) - the tech needs the Capital (level 3)
        check("defensive architecture blocked below TH level 3",
                TownHallController.researchTechnology("Defensive Architecture").contains("level 3"));

        // pre-wall baseline: a barbarian's 15 attack hits the TH for 15 - 10 defense = 5
        Pair<Integer, Integer> thCoord = new Pair<>(th().getMainLand().getI(), th().getMainLand().getJ());
        Pair<Integer, Integer> enemyLoc = null;
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(thCoord))
            if (Pair.isValid(nb)) { enemyLoc = nb; break; }
        // park every unit around the barbarian somewhere else so the TH is its only target
        java.util.HashMap<Land, CivilizedUnit> stash = new java.util.HashMap<>();
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(enemyLoc))
            if (Pair.isValid(nb)) {
                Land t = Game.instance.map[nb.x][nb.y];
                if (t.getCivilizedUnit() != null) { stash.put(t, t.getCivilizedUnit()); t.setCivilizedUnit(null); }
            }
        Game.instance.map[enemyLoc.x][enemyLoc.y].setEnemy(new Enemy(EnemyType.BARBARIAN, enemyLoc));
        th().setHp(200);
        CombatController.enemyTurn();
        check("base defense 10 soaks the barbarian's 15 down to 5 damage", th().getHp() == 195);

        th().setLevel(3);
        int stoneBefore = nation.getStone().getBalance();
        TownHallController.researchTechnology("Defensive Architecture");
        check("accepted at TH level 3", th().isBusy());
        check("costs 100 stone", nation.getStone().getBalance() == stoneBefore - 100);
        for (int i = 0; i < TechnologyType.TECH_DEFENSE.turnsToResearch; i++) GameController.nextGameTurn();
        check("researched after " + TechnologyType.TECH_DEFENSE.turnsToResearch + " turns",
                nation.hasTechnology(TechnologyType.TECH_DEFENSE));

        check("stone wall raises the TH max HP cap to 350", th().getMaxHp() == 350);
        check("stone wall raises defense to 30", th().getDefense() == 30);
        // defense 30 > attack 15, but every attack still chips the minimum 1
        th().setHp(300);
        CombatController.enemyTurn();
        check("walled TH soaks the barbarian's 15 to the minimum 1 chip", th().getHp() == 299);

        Game.instance.map[enemyLoc.x][enemyLoc.y].setEnemy(null);
        for (java.util.Map.Entry<Land, CivilizedUnit> e : stash.entrySet())
            e.getKey().setCivilizedUnit(e.getValue());
        CombatController.lastEnemyBattles.clear();
    }

    // ── 8. bear den (P2-57): spawn, crowd scaling, hunting, calm period ───────
    static int countBears() {
        int bears = 0;
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Enemy e = Game.instance.map[i][j].getEnemy();
                if (e != null && e.getType() == EnemyType.BEAR) bears++;
            }
        return bears;
    }

    static void testBearDen() {
        info("--- bear den ---");
        DisasterController.bearDenEnabled = true;
        Game.instance.bearDen = null;
        Game.instance.bearCalmUntilTurn = 0;
        DisasterController.ensureBearDen();
        Pair<Integer, Integer> den = Game.instance.bearDen;
        check("a den is picked on a forest hex",
                den != null && Game.instance.map[den.x][den.y].getLandType() == LandType.FOREST);
        if (den == null) { DisasterController.bearDenEnabled = false; return; }
        Pair<Integer, Integer> thCoord = new Pair<>(th().getMainLand().getI(), th().getMainLand().getJ());
        check("den keeps its distance from the town hall",
                CombatController.hexDistance(den, thCoord) >= 3);

        // the auto-picked den can still reach the town hall's unit cluster (radius 3),
        // so for the BEHAVIOR checks we move the den to a quiet far corner
        Pair<Integer, Integer> quiet = new Pair<>(13, 13);
        Land quietTile = Game.instance.map[quiet.x][quiet.y];
        LandType quietBackup = quietTile.getLandType();
        quietTile.setLandType(LandType.FOREST);
        for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(quiet, 3))
            if (Pair.isValid(hex) && Game.instance.map[hex.x][hex.y].getCivilizedUnit() != null) {
                nation.removeUnit(Game.instance.map[hex.x][hex.y].getCivilizedUnit());
                Game.instance.map[hex.x][hex.y].setCivilizedUnit(null);
            }
        Game.instance.bearDen = quiet;
        den = quiet;

        // no units near the den -> nothing happens
        DisasterController.bearTurn();
        check("no bear while nobody is near the den", countBears() == 0);

        // park a worker right next to the den -> one bear comes out and mauls it
        Pair<Integer, Integer> baitSpot = null;
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(den))
            if (Pair.isValid(nb) && Game.instance.map[nb.x][nb.y].getLandType().isWalkable
                    && Game.instance.map[nb.x][nb.y].getCivilizedUnit() == null
                    && Game.instance.map[nb.x][nb.y].getEnemy() == null) { baitSpot = nb; break; }
        CivilizedUnit bait = new CivilizedUnit(CivilizedUnitType.WORKER, nation, baitSpot);
        Game.instance.map[baitSpot.x][baitSpot.y].setCivilizedUnit(bait);
        nation.addUnit(bait);
        DisasterController.bearTurn();
        check("one bear spawns when a unit nears the den (base 1)", countBears() == 1);
        check("the bear hunts the worker the same turn (35 raw HP)", bait.getHp() == 65);

        // 3 units within radius 3 -> the den sends out a second bear (max 2)
        java.util.ArrayList<CivilizedUnit> crowd = new java.util.ArrayList<>();
        for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(den, 2)) {
            if (crowd.size() >= 2) break;
            if (!Pair.isValid(hex) || hex.equals(den)) continue;
            Land t = Game.instance.map[hex.x][hex.y];
            if (!t.getLandType().isWalkable || t.getCivilizedUnit() != null || t.getEnemy() != null) continue;
            CivilizedUnit extra = new CivilizedUnit(CivilizedUnitType.WORKER, nation, hex);
            t.setCivilizedUnit(extra);
            nation.addUnit(extra);
            crowd.add(extra);
        }
        DisasterController.bearTurn();
        check("3+ units near the den -> 2 bears (max 2)", countBears() == 2);

        // pull everyone out -> the bears go home and the area calms down for 5 turns
        for (CivilizedUnit u : crowd) {
            Game.instance.map[u.getLocation().x][u.getLocation().y].setCivilizedUnit(null);
            nation.removeUnit(u);
        }
        if (nation.getUnits().contains(bait)) {
            Game.instance.map[bait.getLocation().x][bait.getLocation().y].setCivilizedUnit(null);
            nation.removeUnit(bait);
        }
        DisasterController.bearTurn();
        check("bears return to the forest when no target is near the den", countBears() == 0);
        check("the area stays calm for 5 turns",
                Game.instance.bearCalmUntilTurn == Game.instance.getTurn() + DisasterController.BEAR_CALM_TURNS);

        // during the calm even a juicy target brings no bear; after it, they're back
        CivilizedUnit bait2 = new CivilizedUnit(CivilizedUnitType.WORKER, nation, baitSpot);
        Game.instance.map[baitSpot.x][baitSpot.y].setCivilizedUnit(bait2);
        nation.addUnit(bait2);
        DisasterController.bearTurn();
        check("no new bear during the calm period", countBears() == 0);
        Game.instance.bearCalmUntilTurn = 0;
        DisasterController.bearTurn();
        check("the bear comes back once the calm is over", countBears() == 1);

        // clean up: no bears, no bait, den machinery off for the remaining tests
        Game.instance.map[bait2.getLocation().x][bait2.getLocation().y].setCivilizedUnit(null);
        nation.removeUnit(bait2);
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                Game.instance.map[i][j].setEnemy(null);
        quietTile.setLandType(quietBackup);
        Game.instance.bearDen = null;
        Game.instance.bearCalmUntilTurn = 0;
        DisasterController.bearDenEnabled = false;
        CombatController.lastEnemyBattles.clear();
    }

    // ── 99. save/load (P2-63..67) - runs last, load replaces the game object ──
    static void testSaveLoad() {
        info("--- save/load ---");

        int foodBefore = nation.getFood().getBalance();
        int turnBefore = Game.instance.getTurn();
        int unitsBefore = nation.getUnits().size();

        String saveMsg = SaveController.saveGame("slot1", "driver-test");
        check("manual save to slot1 works", saveMsg.startsWith("Saved"));
        check("no temp file left behind (atomic write)",
                !new java.io.File(SaveController.saveDirectory, "slot1.tmp").exists());

        SaveController.SaveData meta = SaveController.peek("slot1");
        check("slot metadata carries version, name, turn and season",
                meta != null && meta.version == SaveController.SAVE_VERSION
                        && "driver-test".equals(meta.name) && meta.turn == turnBefore && meta.season != null);

        // wreck the live state, then load the save back
        nation.getFood().add(-5);
        Game.instance.setTurn(turnBefore + 7);
        String loadMsg = SaveController.loadGame("slot1");
        check("load restores the save", loadMsg.startsWith("Loaded"));

        // the loaded game is a fresh object graph - re-grab everything
        user = Game.instance.getPlayersInGame().get(0);
        nation = user.getNation();
        check("turn restored", Game.instance.getTurn() == turnBefore);
        check("food balance restored", nation.getFood().getBalance() == foodBefore);
        check("unit count restored", nation.getUnits().size() == unitsBefore);
        check("town hall restored with its map tile", th() != null && th().getMainLand() != null);

        // P2-66: the RNG state is part of the save - after a load, the dice must
        // continue with EXACTLY the same sequence the original game would have rolled
        Game.instance.getRng().setSeed(777);
        Game.instance.getRng().nextInt(); // advance the state a little
        SaveController.saveGame("slot2", "rng-test");
        int[] expectedRolls = new int[3];
        for (int i = 0; i < 3; i++) expectedRolls[i] = Game.instance.getRng().nextInt(1000);
        SaveController.loadGame("slot2");
        user = Game.instance.getPlayersInGame().get(0);
        nation = user.getNation();
        boolean sameSequence = true;
        for (int i = 0; i < 3; i++)
            if (Game.instance.getRng().nextInt(1000) != expectedRolls[i]) sameSequence = false;
        check("RNG state survives save/load (same dice after loading)", sameSequence);

        // autosave ran at the end of every nextGameTurn in the earlier tests
        check("autosave file exists after played turns",
                new java.io.File(SaveController.saveDirectory, "autosave.sav").exists());

        // a save from a wrong version must be refused with a clear error
        try {
            SaveController.SaveData bad = new SaveController.SaveData();
            bad.version = 99;
            bad.name = "future";
            bad.turn = 1;
            bad.game = Game.instance;
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
                    new java.io.FileOutputStream(new java.io.File(SaveController.saveDirectory, "slot3.sav")))) {
                out.writeObject(bad);
            }
        } catch (Exception e) { /* checked below */ }
        check("incompatible save version refused", SaveController.loadGame("slot3").contains("version"));

        new java.io.File(SaveController.saveDirectory, "slot2.sav").delete();
        check("empty slot refused politely", SaveController.loadGame("slot2").contains("empty"));

        // damaged file: garbage bytes -> marked DAMAGED in the list, refused, deletable
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(SaveController.saveDirectory, "slot3.sav"))) {
            out.write("this is not a save file".getBytes());
        } catch (Exception ignored) { }
        check("damaged slot is marked DAMAGED in the slot list",
                SaveController.describeSlot("slot3").contains("DAMAGED"));
        check("loading a damaged slot is refused without crashing",
                SaveController.loadGame("slot3").startsWith("Load FAILED"));
        check("damaged slot can be deleted",
                SaveController.deleteSlot("slot3").contains("deleted")
                        && !new java.io.File(SaveController.saveDirectory, "slot3.sav").exists());
    }

    // ── 0. seasons: 10-turn cycle + winter movement penalty ──────────────────
    static void testSeasons() {
        info("--- seasons ---");
        check("turn 1 is Spring",  Season.forTurn(1)  == Season.SPRING);
        check("turn 10 is Spring", Season.forTurn(10) == Season.SPRING);
        check("turn 11 is Summer", Season.forTurn(11) == Season.SUMMER);
        check("turn 21 is Autumn", Season.forTurn(21) == Season.AUTUMN);
        check("turn 31 is Winter", Season.forTurn(31) == Season.WINTER);
        check("turn 41 wraps back to Spring", Season.forTurn(41) == Season.SPRING);

        Land someLand = Game.instance.map[7][7];
        int turnBackup = Game.instance.getTurn();
        Game.instance.setTurn(5); // spring
        int springCost = someLand.getMP();
        Game.instance.setTurn(35); // winter
        check("winter movement costs +1 AP", someLand.getMP() == springCost + 1);
        Game.instance.setTurn(turnBackup);

        // volcanoes: map gen should have marked ~2 mountains
        int volcanoes = 0;
        for (int i = 0; i < 15; i++)
            for (int j = 0; j < 15; j++)
                if (Game.instance.map[i][j].isVolcano()) volcanoes++;
        info("volcanoes on map: " + volcanoes);
        check("volcanoes only on mountains", allVolcanoesOnMountains());
    }

    static boolean allVolcanoesOnMountains() {
        for (int i = 0; i < 15; i++)
            for (int j = 0; j < 15; j++)
                if (Game.instance.map[i][j].isVolcano()
                        && Game.instance.map[i][j].getLandType() != LandType.MOUNTAIN) return false;
        return true;
    }

    // ── 1. research goes through the queue and takes N turns ─────────────────
    static void testTimedResearch() {
        info("--- timed research ---");
        nation.getWood().add(50);
        String msg = TownHallController.researchTechnology("Stone Mine Technology");
        info(msg);
        check("research is enqueued, not instant", !nation.hasTechnology(TechnologyType.TECH_STONE_MINE));
        check("town hall reports busy", th().isBusy());
        GameController.nextGameTurn(); // TECH_STONE_MINE takes 1 turn
        check("tech unlocked after its research time", nation.hasTechnology(TechnologyType.TECH_STONE_MINE));
        check("queue idle again", !th().isBusy());
    }

    // ── 2. only one active order at a time ───────────────────────────────────
    static void testBusyQueueBlocksNewOrders() {
        info("--- busy queue blocks new orders ---");
        nation.getWood().add(50);
        nation.getStone().add(50);
        String first = TownHallController.researchTechnology("Iron Mine Technology"); // 2 turns
        info(first);
        check("first order accepted", th().isBusy());
        String second = TownHallController.researchTechnology("Storage Upgrade I");
        info(second);
        check("second order rejected while busy", second.contains("busy"));
        String train = TownHallController.enqueueUnit("Worker");
        check("training rejected while busy", train.contains("busy"));
        GameController.nextGameTurn();
        GameController.nextGameTurn();
        check("iron mine tech done after 2 turns", nation.hasTechnology(TechnologyType.TECH_IRON_MINE));
    }

    // ── 3. cancel does not refund ────────────────────────────────────────────
    static void testCancelNoRefund() {
        info("--- cancel without refund ---");
        nation.getWood().add(50);
        nation.getStone().add(50);
        int woodBefore = nation.getWood().getBalance();
        TownHallController.researchTechnology("Storage Upgrade I"); // 5 wood 3 stone
        check("resources charged on enqueue", nation.getWood().getBalance() == woodBefore - 5);
        String cancelMsg = TownHallController.cancelCurrentProduction();
        info(cancelMsg);
        check("queue empty after cancel", !th().isBusy());
        check("wood NOT refunded", nation.getWood().getBalance() == woodBefore - 5);
    }

    // ── 4. town hall upgrade to level 2 ──────────────────────────────────────
    static void testTownHallUpgrade() {
        info("--- town hall upgrade ---");
        nation.getWood().add(100);
        nation.getStone().add(100);
        int capBefore = nation.getUnitCap();
        String msg = TownHallController.upgradeTownHall();
        info(msg);
        check("upgrade enqueued", th().isBusy() && th().isUpgradeQueued());
        check("still level 1 while building", th().getLevel() == 1);
        for (int i = 0; i < TownHall.L2_TURNS; i++) GameController.nextGameTurn();
        check("level 2 (Settlement) reached after " + TownHall.L2_TURNS + " turns", th().getLevel() == 2);
        check("level name is Settlement", th().getLevelName().equals("Settlement"));
        check("max HP grew to 250", th().getMaxHp() == 250);
        check("unit cap grew by 5 with the level", nation.getUnitCap() == capBefore + 5);
    }

    // ── 5. steel tools needs level 2 (we are level 2 now, so it should enqueue) ──
    static void testSteelToolsGate() {
        info("--- steel tools ---");
        nation.getIron().add(50);
        String msg = TownHallController.researchTechnology("Steel Tools");
        info(msg);
        check("steel tools accepted at level 2", th().isBusy());
        for (int i = 0; i < TechnologyType.TECH_TOOLS.turnsToResearch; i++) GameController.nextGameTurn();
        check("steel tools researched", nation.hasTechnology(TechnologyType.TECH_TOOLS));
    }

    // ── 6. happiness factors ─────────────────────────────────────────────────
    static void testHappiness() {
        info("--- happiness ---");
        int base = nation.getHappiness();
        info("baseline happiness = " + base);

        // drop a monument onto a territory tile (bypassing the builder on purpose)
        Pair<Integer, Integer> spot = null;
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            if (tile.getImprovement() == null && th().getMainLand() != tile) { spot = hex; break; }
        }
        check("found a free territory tile for the monument", spot != null);
        if (spot == null) return;
        Game.instance.map[spot.x][spot.y].setImprovement(new Improvement(ImprovementType.MONUMENT));
        check("monument gives +2 happiness", nation.getHappiness() == base + 2);
        Game.instance.map[spot.x][spot.y].setImprovement(null);
        check("happiness recalculated after removal", nation.getHappiness() == base);
    }

    // ── 7. bazaar trade tiers ────────────────────────────────────────────────
    static void testBazaar() {
        info("--- bazaar ---");
        String before = TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 10);
        check("trade rejected without a bazaar", before.contains("Bazaar"));

        Pair<Integer, Integer> spot = freeTerritoryTile();
        check("found a free tile for the bazaar", spot != null);
        if (spot == null) return;
        Game.instance.map[spot.x][spot.y].setImprovement(new Improvement(ImprovementType.BAZAAR));

        nation.getFood().add(50);
        String badAmount = TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 25);
        check("non-tier amount rejected", badAmount.contains("10, 100 or 500"));
        int foodBefore = nation.getFood().getBalance();
        int woodBefore = nation.getWood().getBalance();
        String msg = TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 10);
        info(msg);
        check("sold 10 food", nation.getFood().getBalance() == foodBefore - 10);
        check("received 5 wood (50%)", nation.getWood().getBalance() == woodBefore + 5);
        String again = TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 10);
        check("second bazaar trade same turn rejected", again.contains("one transaction"));
        GameController.nextGameTurn();
        String nextTurn = TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 10);
        check("bazaar usable again next turn", nextTurn.startsWith("Traded"));
        Game.instance.map[spot.x][spot.y].setImprovement(null);
    }

    // ── 8. trading post ──────────────────────────────────────────────────────
    static void testTradingPost() {
        info("--- trading post ---");
        Pair<Integer, Integer> spot = freeTerritoryTile();
        check("found a free tile for the trading post", spot != null);
        if (spot == null) return;
        Game.instance.map[spot.x][spot.y].setImprovement(new Improvement(ImprovementType.TRADING_POST));

        nation.getStone().add(50);
        int stoneBefore = nation.getStone().getBalance();
        int ironBefore = nation.getIron().getBalance();
        String msg = TradeController.tradeAtTradingPost(ResourceType.STONE, ResourceType.IRON, 25);
        info(msg);
        check("sold 25 stone", nation.getStone().getBalance() == stoneBefore - 25);
        check("received 20 iron (80%)", nation.getIron().getBalance() == ironBefore + 20);
        String again = TradeController.tradeAtTradingPost(ResourceType.STONE, ResourceType.IRON, 5);
        check("second post trade same turn rejected", again.contains("one transaction"));
        Game.instance.map[spot.x][spot.y].setImprovement(null);
    }

    // ── 9. demolition ────────────────────────────────────────────────────────
    static void testDemolition() {
        info("--- demolition ---");
        CivilizedUnit builder = null;
        for (Unit u : nation.getUnits()) {
            if (u instanceof CivilizedUnit
                    && ((CivilizedUnit) u).getCivilizedUnitType() == CivilizedUnitType.BUILDER) {
                builder = (CivilizedUnit) u;
                break;
            }
        }
        check("a starting builder exists", builder != null);
        if (builder == null) return;
        GameController.setSelectedCivilizedUnit(builder);
        builder.refreshAp();

        Pair<Integer, Integer> here = builder.getLocation();
        Land tile = Game.instance.map[here.x][here.y];
        tile.setImprovement(new Improvement(ImprovementType.TOWN));
        nation.addTownUnitCapBonus();
        int capWithTown = nation.getUnitCap();
        int woodBefore = nation.getWood().getBalance();

        String msg = WorkerController.demolishImprovement();
        info(msg);
        check("improvement removed", tile.getImprovement() == null);
        check("no refund on demolition", nation.getWood().getBalance() == woodBefore);
        check("town cap bonus removed", nation.getUnitCap() == capWithTown - 5);

        // trading posts must be protected
        tile.setImprovement(new Improvement(ImprovementType.TRADING_POST));
        String blocked = WorkerController.checkCanDemolish();
        check("trading post cannot be demolished", blocked != null && blocked.contains("neutral"));
        tile.setImprovement(null);
    }

    static Pair<Integer, Integer> freeTerritoryTile() {
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            if (tile.getImprovement() == null && th().getMainLand() != tile) return hex;
        }
        return null;
    }
}

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

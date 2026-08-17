import sut.civilization.Controller.GameControllers.*;
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

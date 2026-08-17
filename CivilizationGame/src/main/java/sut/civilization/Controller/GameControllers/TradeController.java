package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.Nation;
import sut.civilization.Model.ModulEnums.ImprovementType;
import sut.civilization.Model.ModulEnums.ResourceType;

/**
 * Phase 2 trade system.
 * Bazaar (own building, needs TH level 2): sell exactly 10 units at 50%, 100 at 60%,
 * or 500 at 70% conversion. Trading Post (neutral, pre-placed; usable once it's inside
 * your territory): any amount at 80%. Each facility: one transaction per turn.
 * Received amount is floored; anything over storage capacity is lost (spec).
 */
public class TradeController extends GameController {

    // ── availability checks (also used by the UI to grey things out) ──────────

    public static boolean hasBazaar(Nation nation) {
        return nation.hasImprovementInTerritory(ImprovementType.BAZAAR);
    }

    public static boolean hasTradingPost(Nation nation) {
        return nation.hasImprovementInTerritory(ImprovementType.TRADING_POST);
    }

    /** Bazaar tier: amount must be exactly 10, 100 or 500 -> 50% / 60% / 70%. */
    public static double bazaarRateFor(int amount) {
        switch (amount) {
            case 10:  return 0.5;
            case 100: return 0.6;
            case 500: return 0.7;
            default:  return 0;
        }
    }

    // ── transactions ──────────────────────────────────────────────────────────

    public static String tradeAtBazaar(ResourceType sell, ResourceType receive, int amount) {
        Nation nation = getCurrentTurnUser().getNation();
        if (!hasBazaar(nation))
            return "You don't have a Bazaar!";
        if (nation.isBazaarUsedThisTurn())
            return "The Bazaar already made its one transaction this turn!";
        double rate = bazaarRateFor(amount);
        if (rate == 0)
            return "The Bazaar only trades exactly 10, 100 or 500 units!";
        return doTrade(nation, sell, receive, amount, rate, true);
    }

    public static String tradeAtTradingPost(ResourceType sell, ResourceType receive, int amount) {
        Nation nation = getCurrentTurnUser().getNation();
        if (!hasTradingPost(nation))
            return "No Trading Post inside your territory!";
        if (nation.isTradingPostUsedThisTurn())
            return "The Trading Post already made its one transaction this turn!";
        if (amount <= 0)
            return "Enter a positive amount to trade!";
        return doTrade(nation, sell, receive, amount, 0.8, false);
    }

    private static String doTrade(Nation nation, ResourceType sell, ResourceType receive,
                                  int amount, double rate, boolean bazaar) {
        if (sell == receive)
            return "Pick two different resources to trade!";
        if (nation.getResource(sell).getBalance() < amount)
            return "Not enough " + sell.name + " in storage (need " + amount + ")!";

        int received = (int) Math.floor(amount * rate); // floor per spec
        nation.getResource(sell).spend(amount);
        nation.getResource(receive).add(received); // clamped at capacity - overflow is lost

        if (bazaar) nation.setBazaarUsedThisTurn(true);
        else nation.setTradingPostUsedThisTurn(true);

        return "Traded " + amount + " " + sell.name + " for " + received + " " + receive.name
                + " (" + (int) (rate * 100) + "%).";
    }
}

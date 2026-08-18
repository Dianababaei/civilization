package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.TechnologyType;

public class TownHallController {

    // ── Tech research (Phase 2: goes through the production queue, takes N turns) ──

    public static String researchTechnology(String techName) {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";

        TechnologyType tech = findTech(techName);
        if (tech == null)
            return "Unknown technology: " + techName + "!";

        if (nation.hasTechnology(tech))
            return tech.name + " is already researched!";

        if (townHall.isBusy())
            return "The Town Hall is busy with " + townHall.getCurrentProduction().getDisplayName()
                    + "! Cancel it first (no refund).";

        if (!tech.isAvailable(nation.getTechnologies()))
            return "Prerequisite not met for " + tech.name + "!";

        if (townHall.getLevel() < tech.requiredTownHallLevel)
            return tech.name + " requires Town Hall level " + tech.requiredTownHallLevel + "!";

        if (!nation.canAfford(0, tech.woodCost, tech.stoneCost, tech.ironCost))
            return "Not enough resources. Need: "
                    + tech.woodCost + " wood, "
                    + tech.stoneCost + " stone, "
                    + tech.ironCost + " iron!";

        boolean ok = townHall.enqueueResearch(tech);
        return ok ? tech.name + " added to production queue (" + tech.turnsToResearch + " turns)."
                  : "Cannot research " + tech.name + " right now!";
    }

    // ── Unit training ─────────────────────────────────────────────────────────

    public static String enqueueUnit(String unitName) {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";

        CivilizedUnitType type = findUnitType(unitName);
        if (type == null)
            return "Unknown unit type: " + unitName + "!";

        if (townHall.isBusy())
            return "The Town Hall is busy with " + townHall.getCurrentProduction().getDisplayName()
                    + "! Cancel it first (no refund).";

        if (!nation.canTrainUnit())
            return "Unit cap reached (" + nation.getUnitCap() + "). Build a Town or upgrade the Town Hall!";

        // phase 2: military units have their own gates
        if (townHall.getLevel() < type.requiredTownHallLevel)
            return type.name + " requires Town Hall level " + type.requiredTownHallLevel + "!";
        if (type.requiresMilitaryStable
                && !nation.hasImprovementInTerritory(sut.civilization.Model.ModulEnums.ImprovementType.MILITARY_STABLE))
            return "Cavalry needs a Military Stable in your territory!";
        if (type.isMilitary && !nation.canTrainMilitaryUnit())
            return "Military cap reached (" + nation.getMilitaryUnitCap() + "). Upgrade the Town Hall!";

        if (nation.getFood().getBalance() < type.productionCostFood
                || nation.getWood().getBalance() < type.productionCostWood)
            return "Not enough resources. Need " + type.productionCostFood + " food"
                    + (type.productionCostWood > 0 ? " + " + type.productionCostWood + " wood" : "") + "!";

        boolean ok = townHall.enqueueUnit(type);
        return ok ? type.name + " added to production queue."
                  : "Cannot train " + type.name + " right now!";
    }

    // ── Town Hall upgrade (Phase 2) ───────────────────────────────────────────

    public static String upgradeTownHall() {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";

        if (townHall.getLevel() >= TownHall.MAX_LEVEL)
            return "Town Hall is already at max level!";
        if (townHall.isBusy())
            return "The Town Hall is busy with " + townHall.getCurrentProduction().getDisplayName()
                    + "! Cancel it first (no refund).";

        int target = townHall.getLevel() + 1;
        int wood  = (target == 2) ? TownHall.L2_WOOD  : TownHall.L3_WOOD;
        int stone = (target == 2) ? TownHall.L2_STONE : TownHall.L3_STONE;
        int iron  = (target == 2) ? TownHall.L2_IRON  : TownHall.L3_IRON;
        int turns = (target == 2) ? TownHall.L2_TURNS : TownHall.L3_TURNS;

        if (!nation.canAfford(0, wood, stone, iron))
            return "Not enough resources. Need: " + wood + " wood, " + stone + " stone, " + iron + " iron!";

        boolean ok = townHall.enqueueTownHallUpgrade();
        return ok ? "Upgrade to " + (target == 2 ? "Settlement" : "Capital")
                        + " added to production queue (" + turns + " turns)."
                  : "Cannot upgrade the Town Hall right now!";
    }

    // ── Cancel current production (Phase 2: no refund per spec) ───────────────

    public static String cancelCurrentProduction() {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";
        if (!townHall.hasQueuedProduction())
            return "The production queue is empty!";

        ProductionOrder cancelled = townHall.cancelCurrent();
        return cancelled.getDisplayName() + " cancelled. Spent resources are NOT refunded.";
    }

    // ── border expander: expand territory ────────────────────────────────────
    // called when player activates a border expander unit. adds all 6 neighbor tiles
    // to the nation's territory and marks the unit as expanded (gets removed next turn)

    // just a check for the UI, true if this expander could actually expand right now
    public static boolean canExpandBorder(CivilizedUnit expander) {
        if (expander == null || expander.getCivilizedUnitType() != sut.civilization.Model.ModulEnums.CivilizedUnitType.BORDER_EXPANDER)
            return false;
        if (expander.hasExpanded() || !expander.hasAp())
            return false;

        Nation nation = expander.getOwnerNation();
        Pair<Integer, Integer> center = expander.getLocation();
        if (!Game.instance.map[center.x][center.y].isExploredBy(nation))
            return false;
        for (Pair<Integer, Integer> neighbor : LandController.getAllNeighborsIndexes(center)) {
            if (Pair.isValid(neighbor) && !Game.instance.map[neighbor.x][neighbor.y].isExploredBy(nation))
                return false;
        }
        return true;
    }

    public static String expandBorder() {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        CivilizedUnit expander = GameController.getSelectedCivilizedUnit();

        if (expander == null)
            return "Select a Border Expander first!";
        if (expander.getCivilizedUnitType() != sut.civilization.Model.ModulEnums.CivilizedUnitType.BORDER_EXPANDER)
            return "Selected unit is not a Border Expander!";
        if (expander.hasExpanded())
            return "This Border Expander has already used its expand ability!";
        if (!expander.hasAp())
            return "Border Expander has no AP left!";

        Pair<Integer, Integer> center = expander.getLocation();
        // can only claim a hex if an explorer specifically revealed it, not just any unit
        // walking past
        if (!Game.instance.map[center.x][center.y].isExploredBy(nation))
            return "This hex hasn't been explored by an Explorer yet!";
        for (Pair<Integer, Integer> neighbor : LandController.getAllNeighborsIndexes(center)) {
            if (Pair.isValid(neighbor) && !Game.instance.map[neighbor.x][neighbor.y].isExploredBy(nation))
                return "Not all neighboring hexes have been explored by an Explorer yet!";
        }

        nation.addToTerritory(center);
        for (Pair<Integer, Integer> neighbor : LandController.getAllNeighborsIndexes(center)) {
            if (Pair.isValid(neighbor))
                nation.addToTerritory(neighbor);
        }

        expander.markExpanded();
        expander.spendAp(expander.getCurrentAp()); // consume all AP
        return "Border expanded! Expander will be removed next turn.";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static TechnologyType findTech(String name) {
        for (TechnologyType t : TechnologyType.values())
            if (t.name.equalsIgnoreCase(name)) return t;
        return null;
    }

    private static CivilizedUnitType findUnitType(String name) {
        for (CivilizedUnitType t : CivilizedUnitType.values())
            if (t.name.equalsIgnoreCase(name)) return t;
        return null;
    }
}

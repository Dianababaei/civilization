package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.TechnologyType;

public class TownHallController {

    // ── Tech research ─────────────────────────────────────────────────────────

    public static String researchTechnology(String techName) {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";

        TechnologyType tech = findTech(techName);
        if (tech == null)
            return "Unknown technology: " + techName;

        if (nation.hasTechnology(tech))
            return tech.name + " already researched.";

        if (!tech.isAvailable(nation.getTechnologies()))
            return "Prerequisite not met for " + tech.name + ".";

        if (!nation.canAfford(0, tech.woodCost, tech.stoneCost, tech.ironCost))
            return "Not enough resources. Need: "
                    + tech.woodCost + " wood, "
                    + tech.stoneCost + " stone, "
                    + tech.ironCost + " iron.";

        boolean done = townHall.researchTechnology(tech);
        return done ? tech.name + " researched!" : "Cannot research " + tech.name + " right now.";
    }

    // ── Unit training ─────────────────────────────────────────────────────────

    public static String enqueueUnit(String unitName) {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();
        if (townHall == null)
            return "No Town Hall built yet!";

        CivilizedUnitType type = findUnitType(unitName);
        if (type == null)
            return "Unknown unit type: " + unitName;

        if (!nation.canTrainUnit())
            return "Unit cap reached (" + nation.getUnitCap() + "). Build a Town to increase cap.";

        if (nation.getFood().getBalance() < type.productionCostFood)
            return "Not enough food. Need " + type.productionCostFood + " food.";

        boolean ok = townHall.enqueueUnit(type);
        return ok ? type.name + " added to production queue."
                  : "Cannot train " + type.name + " right now.";
    }

    // ── BorderExpander: expand territory ─────────────────────────────────────
    // Called when the player activates a BorderExpander unit.
    // Adds all 6 neighbours of the expander's tile to the nation's territory,
    // marks the unit as expanded (it will be removed at the start of next turn).

    /** UI pre-check: true if this BorderExpander could successfully expand right now. */
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
        // Doc: a hex can only be claimed if an Explorer has already revealed it specifically
        // (not just any unit's passing visibility).
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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

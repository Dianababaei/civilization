package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

public class WorkerController extends GameController {

    /** AP a Worker spends to station in an improvement; NOT refunded on leaving (see CivilizedUnit.leaveStation()). */
    public static final int STATION_AP_COST = CivilizedUnit.STATION_AP_COST;

    /**
     * Read-only eligibility check shared by the actual build action and the UI (to grey out
     * options the player can't currently use). Returns null if building {@code type} would
     * succeed right now for the selected Builder, or a reason string if it would not.
     */
    public static String checkCanBuild(ImprovementType type) {
        if (selectedCivilizedUnit == null)
            return "Select a Builder first!";
        if (selectedCivilizedUnit.getCivilizedUnitType() != CivilizedUnitType.BUILDER)
            return "Selected unit is not a Builder!";
        if (!selectedCivilizedUnit.hasAp())
            return "Builder has no AP left!";

        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];

        // Doc: resources on undiscovered hexes can't be used (visibility 0 = fog of war).
        if (tile.getVisibility() == 0)
            return "This hex hasn't been explored yet!";

        // Doc: every structure must be built on a hex inside the player's own territory.
        Nation nation = currentTurnUser.getNation();
        if (!nation.ownsHex(tile.getI(), tile.getJ()))
            return "This hex is outside your territory!";

        if (tile.getImprovement() != null)
            return "There is already an improvement here!";

        // Tech checks
        if (type.requiresStoneTech && !nation.hasTechnology(TechnologyType.TECH_STONE_MINE))
            return "Requires Stone Mine technology!";
        if (type.requiresIronTech && !nation.hasTechnology(TechnologyType.TECH_IRON_MINE))
            return "Requires Iron Mine technology!";
        if (type.requiresTownTech && !nation.hasTechnology(TechnologyType.TECH_TOWN))
            return "Requires Town Construction technology!";

        // Land type check
        if (type.requiredLandType != null && tile.getLandType() != type.requiredLandType)
            return "This improvement requires " + type.requiredLandType.name + " terrain!";

        // Resource check for tile resource requirement
        if (type.requiredTileResource != null &&
                (tile.getResource() == null || tile.getResource().getResourceType() != type.requiredTileResource))
            return "This tile lacks the required resource for " + type.name + "!";

        // Lumber Mill needs wood (every forest tile has it, but a prior mill may have exhausted it)
        if (type == ImprovementType.LUMBER_MILL && tile.isResourceDepleted())
            return "This tile's wood deposit is depleted!";
        if (type.requiredTileResource != null && tile.isResourceDepleted())
            return "This tile's " + type.requiredTileResource.name + " deposit is depleted!";

        // Farm needs wheat/rice (FOOD resource) on meadow; Stable needs livestock (FOOD resource) on plain
        if ((type == ImprovementType.FARM || type == ImprovementType.STABLE) && !tile.hasFoodResource())
            return type.name + " requires a tile with food resources (wheat/rice or livestock)!";
        if ((type == ImprovementType.FARM || type == ImprovementType.STABLE) && tile.isResourceDepleted())
            return "This tile's food deposit is depleted!";

        // Iron Mine needs a mountain tile that also carries iron ore (independent of guaranteed stone)
        if (type == ImprovementType.IRON_MINE && !tile.hasIron())
            return type.name + " requires a mountain tile with iron ore!";
        if (type == ImprovementType.IRON_MINE && tile.isIronDepleted())
            return "This tile's iron deposit is depleted!";

        // Town must sit on a resource-free hex: a meadow with no wheat/rice, a plain with
        // no livestock, or a mountain (mountains always have stone, but "resource-free" here
        // means unmined - already guaranteed since tile.getImprovement() == null above).
        if (type == ImprovementType.TOWN) {
            boolean resourceFree = tile.getLandType() == LandType.MOUNTAIN
                    || ((tile.getLandType() == LandType.MEADOW || tile.getLandType() == LandType.PLAIN)
                        && tile.getResource() == null);
            if (!resourceFree)
                return "Town can only be built on a hex with no resource (empty meadow, plain, or mountain)!";
        }

        // Afford build cost
        if (!nation.canAfford(0, type.buildCostWood, type.buildCostStone, type.buildCostIron))
            return "Not enough resources to build " + type.name + "!";

        return null;
    }

    /**
     * Builder places an improvement on the current tile, spending one charge.
     * Returns a result message. If the builder runs out of charges it is removed.
     */
    public static String builderBuildImprovement(String name) {
        ImprovementType type = null;
        for (ImprovementType t : ImprovementType.values())
            if (t.name.equalsIgnoreCase(name)) { type = t; break; }
        if (type == null)
            return "Unknown improvement: " + name;

        String blocked = checkCanBuild(type);
        if (blocked != null) return blocked;

        Nation nation = currentTurnUser.getNation();
        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];

        nation.spend(0, type.buildCostWood, type.buildCostStone, type.buildCostIron);

        tile.setImprovement(new Improvement(type));
        if (type == ImprovementType.TOWN) nation.addTownUnitCapBonus();
        selectedCivilizedUnit.spendAp(type.buildApCost);

        boolean consumed = selectedCivilizedUnit.useCharge();
        if (consumed) {
            tile.setCivilizedUnit(null);
            nation.getUnits().remove(selectedCivilizedUnit);
            selectedCivilizedUnit = null;
            return type.name + " built! Builder consumed (no charges left).";
        }
        return type.name + " built! Builder has " + selectedCivilizedUnit.getCharges() + " charge(s) remaining.";
    }

    /**
     * Station a Worker inside the improvement on their current tile.
     */
    public static String stationWorker() {
        if (selectedCivilizedUnit == null)
            return "Select a Worker first!";
        if (selectedCivilizedUnit.getCivilizedUnitType() != CivilizedUnitType.WORKER)
            return "Selected unit is not a Worker!";
        if (selectedCivilizedUnit.isStationed())
            return "Worker is already stationed!";
        if (!selectedCivilizedUnit.hasAp(STATION_AP_COST))
            return "Worker doesn't have enough AP to station!";

        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];
        Improvement improvement = tile.getImprovement();
        if (improvement == null)
            return "No improvement on this tile to station in!";
        if (!improvement.canAcceptWorker())
            return improvement.getImprovementType().name + " is at worker capacity!";

        improvement.addWorker(selectedCivilizedUnit);
        selectedCivilizedUnit.stationIn(improvement);
        selectedCivilizedUnit.spendAp(STATION_AP_COST);
        return "Worker stationed in " + improvement.getImprovementType().name + ".";
    }

    /**
     * Remove a stationed Worker from their current improvement.
     */
    public static String unstationWorker() {
        if (selectedCivilizedUnit == null)
            return "Select a Worker first!";
        if (!selectedCivilizedUnit.isStationed())
            return "Worker is not stationed anywhere!";

        Improvement improvement = selectedCivilizedUnit.getStationedIn();
        improvement.removeWorker(selectedCivilizedUnit);
        selectedCivilizedUnit.leaveStation();
        return "Worker removed from " + improvement.getImprovementType().name + ".";
    }

    /**
     * Repair a broken improvement (kept for backward compat with UnitStatus system).
     */
    public static String setWorkerToRepair() {
        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];
        if (tile.getImprovement() == null)
            return "There isn't any improvement here!";
        if (!tile.getImprovement().isBroken())
            return "It isn't broken!";
        tile.getImprovement().setBroken(false);
        return "Improvement repaired.";
    }

    public static void workerRepair(CivilizedUnit worker) {
        Land tile = Game.instance.map[worker.getLocation().x][worker.getLocation().y];
        if (tile.getImprovement() != null)
            tile.getImprovement().setBroken(false);
    }

    // Land features removed from new model - stub kept for backward compat.
    public static String setWorkerToRemoveFeature() {
        return "Land features are not part of the current game.";
    }

    // Road/route concepts removed from new model - stub kept for backward compat.
    public static String setWorkerToRemoveRoute() {
        return "Routes are not part of the current game.";
    }
}

package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

public class WorkerController extends GameController {

    // AP cost for a worker to station in an improvement, doesn't get refunded when they leave
    // (see CivilizedUnit.leaveStation())
    public static final int STATION_AP_COST = CivilizedUnit.STATION_AP_COST;

    // just checks if building would work, doesn't actually build anything. used both by the
    // real build action and by the UI to grey out buttons the player can't use right now.
    // returns null if it'd succeed, otherwise a message explaining why not
    public static String checkCanBuild(ImprovementType type) {
        if (selectedCivilizedUnit == null)
            return "Select a Builder first!";
        if (selectedCivilizedUnit.getCivilizedUnitType() != CivilizedUnitType.BUILDER)
            return "Selected unit is not a Builder!";
        if (!selectedCivilizedUnit.hasAp())
            return "Builder has no AP left!";

        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];

        // can't use resources on hexes we haven't discovered (visibility 0 = fog)
        if (tile.getVisibility() == 0)
            return "This hex hasn't been explored yet!";

        // buildings have to go inside our own territory
        Nation nation = currentTurnUser.getNation();
        if (!nation.ownsHex(tile.getI(), tile.getJ()))
            return "This hex is outside your territory!";

        if (tile.getImprovement() != null)
            return "There is already an improvement here!";

        // phase 2: trading posts are neutral, pre-placed structures - never buildable
        if (type == ImprovementType.TRADING_POST)
            return "Trading Posts are neutral structures and cannot be built!";

        // phase 2: bazaar needs a level 2 town hall (settlement), and only one per nation
        if (type == ImprovementType.BAZAAR) {
            if (nation.getTownHall() == null || nation.getTownHall().getLevel() < 2)
                return "Bazaar requires a level 2 Town Hall (Settlement)!";
            for (Pair<Integer, Integer> hex : nation.getTerritory()) {
                if (!Pair.isValid(hex)) continue;
                Improvement other = Game.instance.map[hex.x][hex.y].getImprovement();
                if (other != null && other.getImprovementType() == ImprovementType.BAZAAR)
                    return "You already have a Bazaar!";
            }
        }

        // tech checks
        if (type.requiresStoneTech && !nation.hasTechnology(TechnologyType.TECH_STONE_MINE))
            return "Requires Stone Mine technology!";
        if (type.requiresIronTech && !nation.hasTechnology(TechnologyType.TECH_IRON_MINE))
            return "Requires Iron Mine technology!";
        if (type.requiresTownTech && !nation.hasTechnology(TechnologyType.TECH_TOWN))
            return "Requires Town Construction technology!";

        // terrain check
        if (type.requiredLandType != null && tile.getLandType() != type.requiredLandType)
            return "This improvement requires " + type.requiredLandType.name + " terrain!";

        // does the tile have the resource this improvement needs
        if (type.requiredTileResource != null &&
                (tile.getResource() == null || tile.getResource().getResourceType() != type.requiredTileResource))
            return "This tile lacks the required resource for " + type.name + "!";

        // lumber mill needs wood - every forest has it but an earlier mill might've used it up
        if (type == ImprovementType.LUMBER_MILL && tile.isResourceDepleted())
            return "This tile's wood deposit is depleted!";
        if (type.requiredTileResource != null && tile.isResourceDepleted())
            return "This tile's " + type.requiredTileResource.name + " deposit is depleted!";

        // farm needs wheat/rice (food) on meadow, stable needs livestock (food) on plain
        if ((type == ImprovementType.FARM || type == ImprovementType.STABLE) && !tile.hasFoodResource())
            return type.name + " requires a tile with food resources (wheat/rice or livestock)!";
        if ((type == ImprovementType.FARM || type == ImprovementType.STABLE) && tile.isResourceDepleted())
            return "This tile's food deposit is depleted!";

        // iron mine needs a mountain tile that also happens to have iron ore
        if (type == ImprovementType.IRON_MINE && !tile.hasIron())
            return type.name + " requires a mountain tile with iron ore!";
        if (type == ImprovementType.IRON_MINE && tile.isIronDepleted())
            return "This tile's iron deposit is depleted!";

        // town needs a hex with literally no resource on it. mountains always have stone
        // (see mapInitializer) so they never count - only meadow/plain with nothing on it works
        if (type == ImprovementType.TOWN) {
            boolean resourceFree = (tile.getLandType() == LandType.MEADOW || tile.getLandType() == LandType.PLAIN)
                    && tile.getResource() == null;
            if (!resourceFree)
                return "Town can only be built on a hex with no resource (empty meadow or plain)!";
        }

        // can we actually afford it
        if (!nation.canAfford(0, type.buildCostWood, type.buildCostStone, type.buildCostIron))
            return "Not enough resources to build " + type.name + "!";

        return null;
    }

    // builder places an improvement on its current tile, uses up one charge.
    // if it's out of charges after this it gets removed
    public static String builderBuildImprovement(String name) {
        ImprovementType type = null;
        for (ImprovementType t : ImprovementType.values())
            if (t.name.equalsIgnoreCase(name)) { type = t; break; }
        if (type == null)
            return "Unknown improvement: " + name + "!";

        String blocked = checkCanBuild(type);
        if (blocked != null) return blocked;

        Nation nation = currentTurnUser.getNation();
        Land tile = Game.instance.map[selectedCivilizedUnit.getLocation().x][selectedCivilizedUnit.getLocation().y];

        nation.spend(0, type.buildCostWood, type.buildCostStone, type.buildCostIron);

        tile.setImprovement(new Improvement(type));
        if (type == ImprovementType.TOWN) nation.addTownUnitCapBonus();
        selectedCivilizedUnit.spendAp(type.buildApCost);

        System.out.println("DEBUG: built " + type.name + " at (" + tile.getI() + "," + tile.getJ() + ")");
        boolean consumed = selectedCivilizedUnit.useCharge();
        if (consumed) {
            tile.setCivilizedUnit(null);
            nation.getUnits().remove(selectedCivilizedUnit);
            selectedCivilizedUnit = null;
            return type.name + " built! Builder consumed (no charges left).";
        }
        return type.name + " built! Builder has " + selectedCivilizedUnit.getCharges() + " charge(s) remaining.";
    }

    // check if stationing the selected worker would work, without actually doing it.
    // used by the UI too so it can grey out the station button instead of erroring after click
    public static String checkCanStation() {
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

        return null;
    }

    // stations a worker in the improvement on its current tile
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

    // removes a stationed worker from whatever improvement they're in
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

    // ── Phase 2: voluntary demolition ─────────────────────────────────────────
    // Builder demolishes a building on its own hex, or (if its hex is empty) on a
    // neighboring hex. Costs 1 AP, no resource refund, stationed workers are freed.
    // Town Hall isn't an Improvement so it's automatically exempt; Trading Posts are
    // neutral and explicitly protected.

    public static final int DEMOLISH_AP_COST = 1;

    /** The tile whose improvement would be demolished, or null if there's no target. */
    public static Land findDemolishTarget() {
        if (selectedCivilizedUnit == null) return null;
        Nation nation = currentTurnUser.getNation();
        Pair<Integer, Integer> here = selectedCivilizedUnit.getLocation();
        Land own = Game.instance.map[here.x][here.y];
        if (own.getImprovement() != null) return own;
        for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(here)) {
            if (!Pair.isValid(nb)) continue;
            if (!nation.ownsHex(nb.x, nb.y)) continue;
            Land tile = Game.instance.map[nb.x][nb.y];
            if (tile.getImprovement() != null
                    && tile.getImprovement().getImprovementType() != ImprovementType.TRADING_POST)
                return tile;
        }
        return null;
    }

    // null = demolition would work, otherwise the reason it can't
    public static String checkCanDemolish() {
        if (selectedCivilizedUnit == null)
            return "Select a Builder first!";
        if (selectedCivilizedUnit.getCivilizedUnitType() != CivilizedUnitType.BUILDER)
            return "Selected unit is not a Builder!";
        if (!selectedCivilizedUnit.hasAp(DEMOLISH_AP_COST))
            return "Builder has no AP left!";

        Land target = findDemolishTarget();
        if (target == null)
            return "No building on this hex or a neighboring hex to demolish!";
        if (target.getImprovement().getImprovementType() == ImprovementType.TRADING_POST)
            return "Trading Posts are neutral and cannot be demolished!";
        Nation nation = currentTurnUser.getNation();
        if (!nation.ownsHex(target.getI(), target.getJ()))
            return "You can only demolish buildings inside your territory!";
        return null;
    }

    public static String demolishImprovement() {
        String blocked = checkCanDemolish();
        if (blocked != null) return blocked;

        Nation nation = currentTurnUser.getNation();
        Land target = findDemolishTarget();
        Improvement imp = target.getImprovement();
        ImprovementType type = imp.getImprovementType();

        // free any stationed workers before the building disappears
        for (CivilizedUnit worker : new java.util.ArrayList<>(imp.getStationedWorkers())) {
            imp.removeWorker(worker);
            worker.leaveStation();
        }
        // demolished towns no longer raise the unit cap
        if (type == ImprovementType.TOWN) nation.removeTownUnitCapBonus();

        target.setImprovement(null);
        selectedCivilizedUnit.spendAp(DEMOLISH_AP_COST);
        return type.name + " demolished. Build costs are NOT refunded.";
    }

    // repairs a broken improvement (kept around for backward compat with UnitStatus)
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

    // land features aren't a thing in the new model anymore, just kept this stub around
    public static String setWorkerToRemoveFeature() {
        return "Land features are not part of the current game.";
    }

    // same deal, roads got cut from the new model
    public static String setWorkerToRemoveRoute() {
        return "Routes are not part of the current game.";
    }
}

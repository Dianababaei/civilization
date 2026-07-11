package sut.civilization.Model.ModulEnums;

/**
 * Buildings that Builders can construct on hex tiles.
 * Each entry defines: which resource it produces, how many per worker per turn,
 * the resource cost to build, the AP cost for the Builder, the max workers it accepts,
 * and which tile types / tile resources it requires.
 *
 * upkeepResource: the ResourceType spent each turn for maintenance (same as produced resource).
 * upkeepAmount:   amount consumed per turn.
 * buildCostWood / buildCostStone / buildCostIron: resources deducted on construction.
 * buildApCost:    AP the Builder spends to place this building.
 * maxWorkers:     max Worker units that can station here simultaneously.
 * requiredLandType:     tile must be this type (null = any walkable).
 * requiredTileResource: tile must carry this resource (null = no constraint).
 * requiresStoneMineUnlocked / requiresIronMineUnlocked: technology gate flags.
 */
public enum ImprovementType {

    // Lumber Mill - forest tiles only, produces WOOD
    LUMBER_MILL(
        "Lumber Mill",
        ResourceType.WOOD,   // produces
        3,                   // production per worker per turn
        ResourceType.WOOD,   // upkeep resource
        1,                   // upkeep amount per turn
        2, 0, 0,             // build cost: wood, stone, iron
        1,                   // AP cost to build
        3,                   // max workers
        LandType.FOREST,     // required land type
        null,                // required tile resource
        false, false, false
    ),

    // Stone Mine - mountain tiles with stone, produces STONE (requires stone tech unlock)
    STONE_MINE(
        "Stone Mine",
        ResourceType.STONE,
        2,
        ResourceType.WOOD,
        1,
        3, 0, 0,
        2,
        3,
        LandType.MOUNTAIN,
        ResourceType.STONE,
        true, false, false
    ),

    // Iron Mine - mountain tiles with iron, produces IRON (requires iron tech unlock)
    IRON_MINE(
        "Iron Mine",
        ResourceType.IRON,
        2,
        ResourceType.WOOD,
        1,
        3, 0, 0,
        2,
        3,
        LandType.MOUNTAIN,
        null,                // iron check is done at runtime via Land.hasIron()
        true, true, false
    ),

    // Farm - meadow tiles with wheat or rice, produces FOOD
    FARM(
        "Farm",
        ResourceType.FOOD,
        3,
        ResourceType.WOOD,
        1,
        2, 0, 0,
        1,
        3,
        LandType.MEADOW,
        null,                // wheat/rice check is done at runtime via Land.hasFoodResource()
        false, false, false
    ),

    // Stable - plain tiles with livestock (cow/sheep), produces FOOD
    STABLE(
        "Stable",
        ResourceType.FOOD,
        2,
        ResourceType.WOOD,
        1,
        2, 0, 0,
        1,
        3,
        LandType.PLAIN,
        null,                // livestock check done at runtime via Land.hasLivestock()
        false, false, false
    ),

    // Town (شهرک) - resource-free tiles, increases Unit Cap; no worker production
    TOWN(
        "Town",
        null,                // produces nothing
        0,
        ResourceType.WOOD,
        1,
        5, 3, 2,            // expensive: 5 wood + 3 stone + 2 iron
        4,                   // high AP cost - deliberately steep, forces a Builder to commit its whole turn
        0,                   // no workers
        null,                // any resource-free tile (checked at runtime)
        null,
        false, false, true
    );

    public final String name;
    public final ResourceType producedResource;   // null for TOWN
    public final int productionPerWorkerPerTurn;
    public final ResourceType upkeepResource;
    public final int upkeepAmount;
    public final int buildCostWood;
    public final int buildCostStone;
    public final int buildCostIron;
    public final int buildApCost;
    public final int maxWorkers;
    public final LandType requiredLandType;        // null = no constraint
    public final ResourceType requiredTileResource; // null = no constraint
    public final boolean requiresStoneTech;
    public final boolean requiresIronTech;
    public final boolean requiresTownTech;

    ImprovementType(String name, ResourceType producedResource, int productionPerWorkerPerTurn,
                    ResourceType upkeepResource, int upkeepAmount,
                    int buildCostWood, int buildCostStone, int buildCostIron,
                    int buildApCost, int maxWorkers,
                    LandType requiredLandType, ResourceType requiredTileResource,
                    boolean requiresStoneTech, boolean requiresIronTech, boolean requiresTownTech) {
        this.name = name;
        this.producedResource = producedResource;
        this.productionPerWorkerPerTurn = productionPerWorkerPerTurn;
        this.upkeepResource = upkeepResource;
        this.upkeepAmount = upkeepAmount;
        this.buildCostWood = buildCostWood;
        this.buildCostStone = buildCostStone;
        this.buildCostIron = buildCostIron;
        this.buildApCost = buildApCost;
        this.maxWorkers = maxWorkers;
        this.requiredLandType = requiredLandType;
        this.requiredTileResource = requiredTileResource;
        this.requiresStoneTech = requiresStoneTech;
        this.requiresIronTech = requiresIronTech;
        this.requiresTownTech = requiresTownTech;
    }
}

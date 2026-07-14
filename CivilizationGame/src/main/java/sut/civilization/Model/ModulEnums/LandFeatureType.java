package sut.civilization.Model.ModulEnums;

public enum LandFeatureType {
    WATERSHED("Watershed", 2, 0, 0, -33, 1, null, true),
    JUNGLE("Jungle", 1, 1, 0, 25, 2, null, true),
    ICE("Ice", 0, 0, 0, 0, 1, null, false),
    FOREST("Forest", 1, -1, 0, 25, 2, null, true),
    MARSH("Marsh", -1, 0, 0, -33, 2, null, true),
    OASIS("Oasis", 3, 0, 1, -33, 1, null, true);

    public String name;
    public int foodGrowth;
    public int productionGrowth;
    public int goldGrowth;
    public int combatChange;
    public int movementCost;
    public ResourceType[] resources;
    public boolean isWalkable;

    LandFeatureType(String name, int foodGrowth, int productionGrowth, int goldGrowth,
                     int combatChange, int movementCost, ResourceType[] resources, boolean isWalkable) {
        this.name = name;
        this.foodGrowth = foodGrowth;
        this.productionGrowth = productionGrowth;
        this.goldGrowth = goldGrowth;
        this.combatChange = combatChange;
        this.movementCost = movementCost;
        this.resources = resources;
        this.isWalkable = isWalkable;
    }
}

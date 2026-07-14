package sut.civilization.Model.ModulEnums;

public enum LandType {
    //          name         movementCost  walkable  hasWood  hasStone  canHaveIron  canHaveFood
    FOREST(     "Forest",    2,            true),
    PLAIN(      "Plain",     1,            true),
    MOUNTAIN(   "Mountain",  4,            true),
    MEADOW(     "Meadow",    1,            true);

    public final String name;
    public final int movementCost;
    public final boolean isWalkable;

    LandType(String name, int movementCost, boolean isWalkable) {
        this.name = name;
        this.movementCost = movementCost;
        this.isWalkable = isWalkable;
    }
}

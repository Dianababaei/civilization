package sut.civilization.Model.ModulEnums;

public enum LandType {
    //          name         movementCost  walkable  hasWood  hasStone  canHaveIron  canHaveFood
    FOREST(     "Forest",    2,            true),
    PLAIN(      "Plain",     1,            true),
    MOUNTAIN(   "Mountain",  4,            true),
    MEADOW(     "Meadow",    1,            true),
    // phase 2: fully impassable - no movement, no buildings, no resources.
    // normal Mountain keeps its phase 1 behavior (walkable, has stone)
    MOUNTAIN_RANGE("Mountain Range", 999,  false);

    public final String name;
    public final int movementCost;
    public final boolean isWalkable;

    LandType(String name, int movementCost, boolean isWalkable) {
        this.name = name;
        this.movementCost = movementCost;
        this.isWalkable = isWalkable;
    }
}

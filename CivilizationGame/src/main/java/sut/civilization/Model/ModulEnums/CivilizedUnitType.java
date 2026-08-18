package sut.civilization.Model.ModulEnums;

public enum CivilizedUnitType {
    //            name              AP  visionRadius  productionCost(food)  turnsToTrain
    EXPLORER(     "Explorer",       4,  3,            2,                    2),
    BUILDER(      "Builder",        4,  1,            3,                    2),
    WORKER(       "Worker",         3,  1,            2,                    1),
    BORDER_EXPANDER("BorderExpander", 2, 1,           4,                    3),

    // phase 2 military units (P2-09..11). Actual combat comes later - for now they
    // can be trained, moved and garrisoned, and they count against the military cap.
    //         name         AP vision food wood turns hp atk range thLvl needsStable
    SWORDSMAN("Swordsman",  2, 1,     20,  10,  2,    1, 10, 1,    1,    false),
    ARCHER(   "Archer",     2, 2,     15,  15,  2,    1, 6,  2,    2,    false),
    CAVALRY(  "Cavalry",    4, 2,     30,  20,  3,    2, 8,  1,    2,    true);

    public final String name;
    /** Action Points granted at the start of each turn */
    public final int ap;
    /** How many hexes away this unit can reveal fog of war */
    public final int visionRadius;
    /** Food consumed from Town Hall storage when training this unit */
    public final int productionCostFood;
    /** Wood consumed when training (phase 2: military units also cost wood) */
    public final int productionCostWood;
    /** Turns the Town Hall queue takes to train this unit */
    public final int turnsToTrain;
    /** Phase 2 military stats. Civil units keep the old default 100 HP and never attack. */
    public final int maxHp;
    public final int attackPower;
    public final int attackRange;
    /** Town Hall level needed before this unit shows up as trainable */
    public final int requiredTownHallLevel;
    /** Cavalry needs at least one Military Stable in your territory */
    public final boolean requiresMilitaryStable;
    public final boolean isMilitary;
    /** Image path for UI rendering */
    public final String imageAddress;
    /** Actions available to this unit type in the UI */
    public UnitActions[] actions;

    // civil units - no combat stats, no wood cost, trainable from level 1
    CivilizedUnitType(String name, int ap, int visionRadius, int productionCostFood, int turnsToTrain) {
        this(name, ap, visionRadius, productionCostFood, 0, turnsToTrain, 100, 0, 0, 1, false);
    }

    CivilizedUnitType(String name, int ap, int visionRadius, int productionCostFood, int productionCostWood,
                      int turnsToTrain, int maxHp, int attackPower, int attackRange,
                      int requiredTownHallLevel, boolean requiresMilitaryStable) {
        this.name = name;
        this.ap = ap;
        this.visionRadius = visionRadius;
        this.productionCostFood = productionCostFood;
        this.productionCostWood = productionCostWood;
        this.turnsToTrain = turnsToTrain;
        this.maxHp = maxHp;
        this.attackPower = attackPower;
        this.attackRange = attackRange;
        this.requiredTownHallLevel = requiredTownHallLevel;
        this.requiresMilitaryStable = requiresMilitaryStable;
        this.isMilitary = attackPower > 0;
        this.imageAddress = "/sut/civilization/Images/units/" + name + ".png";
        switch (name) {
            case "Explorer":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.SLEEP, UnitActions.WAKE};
                break;
            case "Builder":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.BUILD_IMPROVEMENT, UnitActions.BUILD_ROAD, UnitActions.DEMOLISH, UnitActions.SLEEP, UnitActions.WAKE};
                break;
            case "Worker":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.REPAIR, UnitActions.SLEEP, UnitActions.WAKE};
                break;
            case "BorderExpander":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.FOUND_CITY, UnitActions.SLEEP, UnitActions.WAKE};
                break;
            default:
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.SLEEP, UnitActions.WAKE};
        }
    }
}

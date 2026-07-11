package sut.civilization.Model.ModulEnums;

public enum CivilizedUnitType {
    //            name              AP  visionRadius  productionCost(food)  turnsToTrain
    EXPLORER(     "Explorer",       4,  3,            2,                    2),
    BUILDER(      "Builder",        4,  1,            3,                    2),
    WORKER(       "Worker",         3,  1,            2,                    1),
    BORDER_EXPANDER("BorderExpander", 2, 1,           4,                    3);

    public final String name;
    /** Action Points granted at the start of each turn */
    public final int ap;
    /** How many hexes away this unit can reveal fog of war */
    public final int visionRadius;
    /** Food consumed from Town Hall storage when training this unit */
    public final int productionCostFood;
    /** Turns the Town Hall queue takes to train this unit */
    public final int turnsToTrain;
    /** Image path for UI rendering */
    public final String imageAddress;
    /** Actions available to this unit type in the UI */
    public UnitActions[] actions;

    CivilizedUnitType(String name, int ap, int visionRadius, int productionCostFood, int turnsToTrain) {
        this.name = name;
        this.ap = ap;
        this.visionRadius = visionRadius;
        this.productionCostFood = productionCostFood;
        this.turnsToTrain = turnsToTrain;
        this.imageAddress = "/sut/civilization/Images/units/" + name + ".png";
        switch (name) {
            case "Explorer":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.SLEEP, UnitActions.WAKE};
                break;
            case "Builder":
                this.actions = new UnitActions[]{UnitActions.MOVE, UnitActions.BUILD_IMPROVEMENT, UnitActions.SLEEP, UnitActions.WAKE};
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

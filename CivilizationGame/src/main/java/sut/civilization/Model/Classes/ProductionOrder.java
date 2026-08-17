package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.TechnologyType;

/**
 * One order in the Town Hall's single production queue (Phase 2).
 * The queue handles three kinds of work: training a unit, researching a
 * technology, or upgrading the Town Hall itself. Costs are paid when the
 * order is enqueued; cancelling does NOT refund them (per spec).
 */
public class ProductionOrder {

    public enum Kind { TRAIN_UNIT, RESEARCH_TECH, UPGRADE_TOWN_HALL }

    private final Kind kind;
    private final CivilizedUnitType unitType;   // only for TRAIN_UNIT
    private final TechnologyType tech;          // only for RESEARCH_TECH
    private final int targetLevel;              // only for UPGRADE_TOWN_HALL
    private final int totalTurns;               // for the HUD progress bar
    private int turnsLeft;

    private ProductionOrder(Kind kind, CivilizedUnitType unitType, TechnologyType tech,
                            int targetLevel, int turnsLeft) {
        this.kind = kind;
        this.unitType = unitType;
        this.tech = tech;
        this.targetLevel = targetLevel;
        this.totalTurns = Math.max(1, turnsLeft);
        this.turnsLeft = turnsLeft;
    }

    public static ProductionOrder trainUnit(CivilizedUnitType type) {
        return new ProductionOrder(Kind.TRAIN_UNIT, type, null, 0, type.turnsToTrain);
    }

    public static ProductionOrder research(TechnologyType tech) {
        return new ProductionOrder(Kind.RESEARCH_TECH, null, tech, 0, tech.turnsToResearch);
    }

    public static ProductionOrder upgradeTownHall(int targetLevel, int turns) {
        return new ProductionOrder(Kind.UPGRADE_TOWN_HALL, null, null, targetLevel, turns);
    }

    public Kind getKind() { return kind; }
    public CivilizedUnitType getUnitType() { return unitType; }
    public TechnologyType getTech() { return tech; }
    public int getTargetLevel() { return targetLevel; }

    public int getTotalTurns() { return totalTurns; }
    public int getTurnsLeft() { return turnsLeft; }
    public void setTurnsLeft(int t) { turnsLeft = t; }
    public void tick() { turnsLeft--; }
    public boolean isDone() { return turnsLeft <= 0; }

    /** Short label for the HUD / Town Hall panel. */
    public String getDisplayName() {
        switch (kind) {
            case TRAIN_UNIT: return unitType.name;
            case RESEARCH_TECH: return tech.name;
            case UPGRADE_TOWN_HALL: return "Town Hall Lv." + targetLevel;
            default: return "?";
        }
    }
}

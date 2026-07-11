package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.ImprovementType;

public class CivilizedUnit extends Unit {

    private CivilizedUnitType civilizedUnitType;

    // ── Builder fields ────────────────────────────────────────────────────────
    /** Remaining build charges. Builders are consumed when this reaches 0. */
    private int charges;
    private static final int BUILDER_MAX_CHARGES = 3;

    // ── Worker fields ─────────────────────────────────────────────────────────
    /**
     * The building this Worker is currently stationed in.
     * null means the Worker is idle (on the map, not producing).
     */
    private Improvement stationedIn = null;

    /** AP a Worker spends to station in an improvement; NOT refunded on leaving (see leaveStation()). */
    public static final int STATION_AP_COST = 1;

    // ── BorderExpander fields ─────────────────────────────────────────────────
    /** Set to true once the expander uses its expand ability (then it is consumed). */
    private boolean hasExpanded = false;

    public CivilizedUnit(CivilizedUnitType type, Nation ownerNation, Pair<Integer, Integer> location) {
        super(type.name, type.ap, location, ownerNation);
        this.civilizedUnitType = type;
        this.charges = (type == CivilizedUnitType.BUILDER) ? BUILDER_MAX_CHARGES : 0;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public int getCharges() { return charges; }

    /**
     * Consume one charge. Returns true if the builder should now be removed from the map.
     */
    public boolean useCharge() {
        if (civilizedUnitType != CivilizedUnitType.BUILDER) return false;
        charges--;
        return charges <= 0;
    }

    // ── Worker ────────────────────────────────────────────────────────────────

    public Improvement getStationedIn() { return stationedIn; }

    public boolean isStationed() { return stationedIn != null; }

    /**
     * Station this Worker inside a building. Deducts station AP cost from current AP.
     * That AP is NOT refunded when the worker leaves (see leaveStation()).
     */
    public void stationIn(Improvement building) {
        this.stationedIn = building;
        // Stationing costs AP - handled by caller after validating enough AP exists
    }

    /**
     * Unstation this Worker. Doc: leaving a building does NOT refund the AP spent
     * to station there - the reduced AP stays reduced for the rest of the turn.
     */
    public void leaveStation() {
        this.stationedIn = null;
    }

    // ── BorderExpander ────────────────────────────────────────────────────────

    public boolean hasExpanded() { return hasExpanded; }

    public void markExpanded() { this.hasExpanded = true; }

    // ── Common ────────────────────────────────────────────────────────────────

    public CivilizedUnitType getCivilizedUnitType() { return civilizedUnitType; }
}

package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ImprovementType;

import java.util.ArrayList;

public class Improvement {

    private ImprovementType improvementType;
    private boolean isBroken = false;

    // How many consecutive turns upkeep could not be paid - destruction at 3
    private int missedUpkeepTurns = 0;

    // Workers currently stationed here
    private final ArrayList<CivilizedUnit> stationedWorkers = new ArrayList<>();

    public Improvement(ImprovementType improvementType) {
        this.improvementType = improvementType;
    }

    // ── Upkeep / destruction ──────────────────────────────────────────────────

    public int getMissedUpkeepTurns() { return missedUpkeepTurns; }

    public void recordMissedUpkeep() { missedUpkeepTurns++; }

    public void resetMissedUpkeep() { missedUpkeepTurns = 0; }

    public boolean shouldBeDestroyed() { return missedUpkeepTurns >= 3; }

    // ── Worker stations ───────────────────────────────────────────────────────

    public boolean canAcceptWorker() {
        return stationedWorkers.size() < improvementType.maxWorkers;
    }

    public boolean addWorker(CivilizedUnit worker) {
        if (!canAcceptWorker()) return false;
        stationedWorkers.add(worker);
        return true;
    }

    public void removeWorker(CivilizedUnit worker) {
        stationedWorkers.remove(worker);
    }

    public int getWorkerCount() { return stationedWorkers.size(); }

    public ArrayList<CivilizedUnit> getStationedWorkers() { return stationedWorkers; }

    // ── Production ────────────────────────────────────────────────────────────

    /** Base amount this building's workers would produce this turn, before any deposit cap. */
    public int calcBaseProduction() {
        if (isBroken || improvementType.producedResource == null) return 0;
        return stationedWorkers.size() * improvementType.productionPerWorkerPerTurn;
    }

    /**
     * Draws {@code wanted} units out of the underlying tile's resource deposit and returns
     * how much was actually available. Extractive buildings (Lumber Mill, Stone Mine, Iron
     * Mine, Farm, Stable) draw down {@code tileResource}; Town has no tile resource and gets
     * the full requested amount unconstrained.
     */
    public int extractProduction(int wanted, Resource tileResource) {
        if (wanted <= 0) return 0;
        if (tileResource == null) return wanted; // no depletable deposit tracked for this tile
        return tileResource.extract(wanted);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public ImprovementType getImprovementType() { return improvementType; }
    public boolean isBroken() { return isBroken; }
    public void setBroken(boolean broken) { isBroken = broken; }
}

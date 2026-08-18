package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.UnitStatus;

public class Unit implements java.io.Serializable {
    protected Nation ownerNation;
    protected String name;
    protected Pair<Integer, Integer> location;

    protected int maxAp;
    protected int currentAp;

    protected UnitStatus unitStatus;

    // Movement path (sequence of direction digits, consumed as unit moves)
    protected String path = "";

    // HP (used by combat units; civilized units default to 100)
    protected int hp = 100;

    public Unit(String name, int maxAp, Pair<Integer, Integer> location, Nation ownerNation) {
        this.name = name;
        this.maxAp = maxAp;
        this.currentAp = maxAp;
        this.location = location;
        this.ownerNation = ownerNation;
        this.unitStatus = UnitStatus.AWAKE;
    }

    public void refreshAp() {
        currentAp = maxAp;
        // During starvation units lose 1 AP due to weakness (spec: units lose some AP)
        if (ownerNation != null && ownerNation.isInStarvation()) {
            currentAp = Math.max(0, currentAp - 1);
        }
        if (unitStatus == UnitStatus.MOVING) unitStatus = UnitStatus.AWAKE;
    }

    public boolean hasAp() { return currentAp > 0; }
    public boolean hasAp(int required) { return currentAp >= required; }
    public void spendAp(int amount) { currentAp = Math.max(0, currentAp - amount); }

    public boolean isIdle() { return unitStatus == UnitStatus.AWAKE && currentAp > 0; }

    // ── Getters & setters ─────────────────────────────────────────────────────

    public String getName() { return name; }
    public Nation getOwnerNation() { return ownerNation; }
    public Pair<Integer, Integer> getLocation() { return location; }
    public int getMaxAp() { return maxAp; }
    public int getCurrentAp() { return currentAp; }
    public UnitStatus getUnitStatus() { return unitStatus; }
    public String getPath() { return path; }
    public int getHp() { return hp; }

    public void setLocation(Pair<Integer, Integer> location) { this.location = location; }
    public void setOwnerNation(Nation ownerNation) { this.ownerNation = ownerNation; }
    public void setUnitStatus(UnitStatus status) { this.unitStatus = status; }
    public void setCurrentAp(int ap) { this.currentAp = ap; }
    public void setPath(String path) { this.path = path; }
    public void setHp(int hp) { this.hp = hp; }

    // Legacy aliases used by old movement code
    public int getMP() { return currentAp; }
    public void setMP(int mp) { this.currentAp = mp; }
    public void decreaseMP(int amount) { spendAp(amount); }
}

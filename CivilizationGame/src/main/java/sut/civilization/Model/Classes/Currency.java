package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ResourceType;

/**
 * Tracks one resource's stored amount, storage cap, and net production rate per turn.
 * Called "Currency" to preserve existing references throughout the codebase.
 */
public class Currency implements java.io.Serializable {
    private int balance;       // current stored amount
    private int capacity;      // max storable amount
    private int netRate;       // net production per turn (production - upkeep); can be negative

    // FOOD is allowed to dip below 0 so the spec's literal starvation check
    // ("net rate negative AND balance < 0") is actually reachable; other
    // resources stay floored at 0 since nothing consumes them unconditionally.
    private final boolean floorAtZero;

    public Currency(ResourceType resourceType, int initialBalance, int capacity) {
        this.balance = initialBalance;
        this.capacity = capacity;
        this.netRate = 0;
        this.floorAtZero = resourceType != ResourceType.FOOD;
    }

    // Called each turn: add netRate, clamp to capacity (and to 0 for non-food resources)
    public void applyTurnProduction() {
        int updated = balance + netRate;
        if (floorAtZero) updated = Math.max(0, updated);
        balance = Math.min(capacity, updated);
    }

    // Spend resources - returns true if successful, false if not enough
    public boolean spend(int amount) {
        if (balance < amount) return false;
        balance -= amount;
        return true;
    }

    public void add(int amount) {
        balance = Math.min(capacity, balance + amount);
    }

    public void addToNetRate(int delta) {
        netRate += delta;
    }

    public void resetNetRate() {
        netRate = 0;
    }

    public int getBalance() { return balance; }
    public int getCapacity() { return capacity; }
    public int getNetRate() { return netRate; }

    public void setBalance(int balance) { this.balance = Math.min(capacity, balance); }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setNetRate(int netRate) { this.netRate = netRate; }
}

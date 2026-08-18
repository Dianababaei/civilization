package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ResourceType;

public class Resource implements java.io.Serializable {
    /** Remaining extractable quantity of this hex's deposit; production stops at 0. */
    protected int balance;

    protected ResourceType resourceType;

    /** Every tile deposit starts with this many units of its resource. */
    public static final int DEFAULT_DEPOSIT = 30;

    public Resource(ResourceType resourceType){
        this.resourceType = resourceType;
        this.balance = DEFAULT_DEPOSIT;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public boolean isDepleted() {
        return balance <= 0;
    }

    /** Extract up to {@code amount} units; returns how much was actually available. */
    public int extract(int amount) {
        int taken = Math.min(balance, amount);
        balance -= taken;
        return taken;
    }
}

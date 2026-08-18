package sut.civilization.Model.Classes;

import java.util.Random;

public class Ruin implements java.io.Serializable {
    private boolean retrieved;
    private int goldAmount;

    public Ruin() {
        this.retrieved = false;
        this.goldAmount = new Random().nextInt(10);
    }

    public boolean isRetrieved() {
        return retrieved;
    }

    public int getGoldAmount() {
        return goldAmount;
    }

    public void retrieve() {
        this.retrieved = true;
    }
}

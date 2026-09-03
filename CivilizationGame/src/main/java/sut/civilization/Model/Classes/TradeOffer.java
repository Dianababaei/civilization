package sut.civilization.Model.Classes;

import java.util.UUID;

/**
 * Phase 3 player-to-player trade (P3-35..37). Lives on the server's Game and
 * rides every snapshot, so both players always see the same inbox. Like every
 * server data entity it carries a unique id and a createdAt stamp (P3-53) -
 * the id is how accept/reject/cancel requests point at one exact offer.
 */
public class TradeOffer implements java.io.Serializable {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    public final String id = UUID.randomUUID().toString();
    public final long createdAt = System.currentTimeMillis();

    public final String from;
    public final String to;
    // what "from" gives away (locked by the server while the offer waits)
    public final int giveFood, giveWood, giveStone, giveIron;
    // what "from" asks for in return
    public final int wantFood, wantWood, wantStone, wantIron;
    private String status = PENDING;

    public TradeOffer(String from, String to,
                      int giveFood, int giveWood, int giveStone, int giveIron,
                      int wantFood, int wantWood, int wantStone, int wantIron) {
        this.from = from;
        this.to = to;
        this.giveFood = giveFood;
        this.giveWood = giveWood;
        this.giveStone = giveStone;
        this.giveIron = giveIron;
        this.wantFood = wantFood;
        this.wantWood = wantWood;
        this.wantStone = wantStone;
        this.wantIron = wantIron;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    private static String describe(int food, int wood, int stone, int iron) {
        StringBuilder sb = new StringBuilder();
        if (food > 0) sb.append(food).append(" food, ");
        if (wood > 0) sb.append(wood).append(" wood, ");
        if (stone > 0) sb.append(stone).append(" stone, ");
        if (iron > 0) sb.append(iron).append(" iron, ");
        if (sb.length() == 0) return "nothing";
        return sb.substring(0, sb.length() - 2);
    }

    public String describeGive() {
        return describe(giveFood, giveWood, giveStone, giveIron);
    }

    public String describeWant() {
        return describe(wantFood, wantWood, wantStone, wantIron);
    }

    @Override
    public String toString() {
        return from + " gives " + describeGive() + " for " + describeWant() + " from " + to;
    }
}

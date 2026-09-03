package sut.civilization.Network.Messages;

/** Client -> server: explicit trade offer to another player - give X for Y (P3-35). */
public class TradeOfferRequest extends Message {
    public static final String TYPE = "TradeOfferRequest";

    private final String to;
    private final int giveFood, giveWood, giveStone, giveIron;
    private final int wantFood, wantWood, wantStone, wantIron;

    public TradeOfferRequest(String to,
                             int giveFood, int giveWood, int giveStone, int giveIron,
                             int wantFood, int wantWood, int wantStone, int wantIron) {
        super(TYPE);
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

    public String getTo() {
        return to;
    }

    public int getGiveFood() { return giveFood; }
    public int getGiveWood() { return giveWood; }
    public int getGiveStone() { return giveStone; }
    public int getGiveIron() { return giveIron; }
    public int getWantFood() { return wantFood; }
    public int getWantWood() { return wantWood; }
    public int getWantStone() { return wantStone; }
    public int getWantIron() { return wantIron; }
}

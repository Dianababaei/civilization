package sut.civilization.Network.Messages;

/** Client -> server: unit on (fromX,fromY) attacks the hex (toX,toY) - PvP or barbarian/bear (P3-31). */
public class AttackRequest extends Message {
    public static final String TYPE = "AttackRequest";

    private final int fromX;
    private final int fromY;
    private final int toX;
    private final int toY;

    public AttackRequest(int fromX, int fromY, int toX, int toY) {
        super(TYPE);
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
    }

    public int getFromX() {
        return fromX;
    }

    public int getFromY() {
        return fromY;
    }

    public int getToX() {
        return toX;
    }

    public int getToY() {
        return toY;
    }
}

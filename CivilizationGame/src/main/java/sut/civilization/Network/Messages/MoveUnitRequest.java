package sut.civilization.Network.Messages;

/** Client -> server: move own unit standing on (fromX,fromY) to (toX,toY). */
public class MoveUnitRequest extends Message {
    public static final String TYPE = "MoveUnitRequest";

    private final int fromX;
    private final int fromY;
    private final int toX;
    private final int toY;

    public MoveUnitRequest(int fromX, int fromY, int toX, int toY) {
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

package sut.civilization.Network.Messages;

/**
 * Client -> server: station the Worker standing on (x,y) into the improvement
 * on its tile (or pull it back out) - without this, farms and mines built in
 * multiplayer would never produce anything.
 */
public class StationRequest extends Message {
    public static final String TYPE = "StationRequest";

    private final int x;
    private final int y;
    private final boolean station;

    public StationRequest(int x, int y, boolean station) {
        super(TYPE);
        this.x = x;
        this.y = y;
        this.station = station;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isStation() {
        return station;
    }
}

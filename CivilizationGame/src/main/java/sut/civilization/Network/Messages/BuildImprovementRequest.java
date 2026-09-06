package sut.civilization.Network.Messages;

/**
 * Client -> server: the Builder standing on (x,y) builds the named improvement
 * there. The server re-runs every phase 1/2 check (terrain, tech, resources,
 * AP, charges) and answers with the exact reason when it refuses.
 */
public class BuildImprovementRequest extends Message {
    public static final String TYPE = "BuildImprovementRequest";

    private final int x;
    private final int y;
    private final String improvementName;

    public BuildImprovementRequest(int x, int y, String improvementName) {
        super(TYPE);
        this.x = x;
        this.y = y;
        this.improvementName = improvementName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getImprovementName() {
        return improvementName;
    }
}

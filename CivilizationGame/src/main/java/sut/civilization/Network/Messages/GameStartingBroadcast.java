package sut.civilization.Network.Messages;

/** Server -> everyone: all checks passed, the match is starting on this map. */
public class GameStartingBroadcast extends Message {
    public static final String TYPE = "GameStartingBroadcast";

    private final String mapName;

    public GameStartingBroadcast(String mapName) {
        super(TYPE);
        this.mapName = mapName;
    }

    public String getMapName() {
        return mapName;
    }
}

package sut.civilization.Network.Messages;

/** Client -> server: host picks one of the pre-designed maps. */
public class ChooseMapRequest extends Message {
    public static final String TYPE = "ChooseMapRequest";

    private final String mapName;

    public ChooseMapRequest(String mapName) {
        super(TYPE);
        this.mapName = mapName;
    }

    public String getMapName() {
        return mapName;
    }
}

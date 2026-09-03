package sut.civilization.Network.Messages;

/**
 * Client -> server: queue one unit in the player's Town Hall (spec flow: armies
 * are TRAINED from gathered resources, not handed out). The server re-runs every
 * phase 2 check - resources, unit caps, Town Hall level, stable requirement -
 * and answers with the exact reason when it refuses.
 */
public class TrainUnitRequest extends Message {
    public static final String TYPE = "TrainUnitRequest";

    private final String unitName;

    public TrainUnitRequest(String unitName) {
        super(TYPE);
        this.unitName = unitName;
    }

    public String getUnitName() {
        return unitName;
    }
}

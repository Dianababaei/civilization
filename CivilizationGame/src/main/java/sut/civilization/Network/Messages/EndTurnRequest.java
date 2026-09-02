package sut.civilization.Network.Messages;

/** Client -> server: the current player ends their turn. */
public class EndTurnRequest extends Message {
    public static final String TYPE = "EndTurnRequest";

    public EndTurnRequest() {
        super(TYPE);
    }
}

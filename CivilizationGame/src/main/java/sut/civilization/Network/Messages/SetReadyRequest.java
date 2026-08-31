package sut.civilization.Network.Messages;

/** Client -> server: toggle the Ready/Not Ready state in the lobby. */
public class SetReadyRequest extends Message {
    public static final String TYPE = "SetReadyRequest";

    private final boolean ready;

    public SetReadyRequest(boolean ready) {
        super(TYPE);
        this.ready = ready;
    }

    public boolean isReady() {
        return ready;
    }
}

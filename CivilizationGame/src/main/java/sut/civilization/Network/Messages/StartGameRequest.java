package sut.civilization.Network.Messages;

/** Client -> server: host asks to start the game (server re-checks everything). */
public class StartGameRequest extends Message {
    public static final String TYPE = "StartGameRequest";

    public StartGameRequest() {
        super(TYPE);
    }
}

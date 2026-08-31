package sut.civilization.Network.Messages;

/** Client -> server: first message on connect, asks to enter the lobby. */
public class JoinLobbyRequest extends Message {
    public static final String TYPE = "JoinLobbyRequest";

    private final String username;

    public JoinLobbyRequest(String username) {
        super(TYPE);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

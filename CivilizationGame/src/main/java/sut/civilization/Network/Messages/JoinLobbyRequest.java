package sut.civilization.Network.Messages;

/** Client -> server: first message on connect, asks to enter the lobby. */
public class JoinLobbyRequest extends Message {
    public static final String TYPE = "JoinLobbyRequest";

    private final String username;
    /** P3-51: first login with a name registers this password; later logins must match it. */
    private final String password;

    public JoinLobbyRequest(String username) {
        this(username, "");
    }

    public JoinLobbyRequest(String username, String password) {
        super(TYPE);
        this.username = username;
        this.password = password == null ? "" : password;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }
}

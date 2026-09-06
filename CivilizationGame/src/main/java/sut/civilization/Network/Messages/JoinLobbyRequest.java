package sut.civilization.Network.Messages;

/** Client -> server: first message on connect, asks to enter the lobby. */
public class JoinLobbyRequest extends Message {
    public static final String TYPE = "JoinLobbyRequest";

    private final String username;
    /** P3-51: first login with a name registers this password; later logins must match it. */
    private final String password;
    /** "login" = account must exist, "signup" = account must NOT exist,
     *  "auto" = old behavior (login if known, register otherwise) - used by tests. */
    private final String mode;

    public JoinLobbyRequest(String username) {
        this(username, "", "auto");
    }

    public JoinLobbyRequest(String username, String password) {
        this(username, password, "auto");
    }

    public JoinLobbyRequest(String username, String password, String mode) {
        super(TYPE);
        this.username = username;
        this.password = password == null ? "" : password;
        this.mode = mode == null ? "auto" : mode;
    }

    public String getMode() {
        return mode;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }
}

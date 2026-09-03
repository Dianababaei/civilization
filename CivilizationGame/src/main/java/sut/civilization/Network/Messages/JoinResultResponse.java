package sut.civilization.Network.Messages;

/** Server -> client: answer to a JoinLobbyRequest. */
public class JoinResultResponse extends Message {
    public static final String TYPE = "JoinResultResponse";

    private final boolean ok;
    private final String reason;   // filled only when ok == false
    private final String username; // the accepted (trimmed) username
    private final boolean host;
    /** P3-51: the signed JWT the client must attach to every later request. */
    private final String authToken;

    public JoinResultResponse(boolean ok, String reason, String username, boolean host) {
        this(ok, reason, username, host, null);
    }

    public JoinResultResponse(boolean ok, String reason, String username, boolean host, String authToken) {
        super(TYPE);
        this.ok = ok;
        this.reason = reason;
        this.username = username;
        this.host = host;
        this.authToken = authToken;
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean isOk() {
        return ok;
    }

    public String getReason() {
        return reason;
    }

    public String getUsername() {
        return username;
    }

    public boolean isHost() {
        return host;
    }
}

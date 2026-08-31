package sut.civilization.Network.Messages;

/** Server -> client: answer to a JoinLobbyRequest. */
public class JoinResultResponse extends Message {
    public static final String TYPE = "JoinResultResponse";

    private final boolean ok;
    private final String reason;   // filled only when ok == false
    private final String username; // the accepted (trimmed) username
    private final boolean host;

    public JoinResultResponse(boolean ok, String reason, String username, boolean host) {
        super(TYPE);
        this.ok = ok;
        this.reason = reason;
        this.username = username;
        this.host = host;
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

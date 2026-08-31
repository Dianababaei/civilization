package sut.civilization.Network.Messages;

/**
 * Server -> client: a request was rejected. The reason must be specific
 * ("It is not your turn", "Only the host can choose the map", ...) and the
 * client shows it to the player unchanged.
 */
public class ErrorResponse extends Message {
    public static final String TYPE = "ErrorResponse";

    private final String reason;

    public ErrorResponse(String reason) {
        super(TYPE);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}

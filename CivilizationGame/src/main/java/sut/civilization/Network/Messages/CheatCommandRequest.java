package sut.civilization.Network.Messages;

/**
 * Client -> server: a cheat typed into the chat box (P3-48 bonus). The client
 * NEVER executes it locally - it detects the leading "/" and sends the raw
 * command here; the server checks the sender is the host, applies the effect
 * to the authoritative game and announces it to every player.
 */
public class CheatCommandRequest extends Message {
    public static final String TYPE = "CheatCommandRequest";

    private final String text;

    public CheatCommandRequest(String text) {
        super(TYPE);
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

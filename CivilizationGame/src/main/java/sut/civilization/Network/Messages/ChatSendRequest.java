package sut.civilization.Network.Messages;

/** Client -> server: send a text message to the public chat channel. */
public class ChatSendRequest extends Message {
    public static final String TYPE = "ChatSendRequest";

    private final String text;

    public ChatSendRequest(String text) {
        super(TYPE);
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

package sut.civilization.Network.Messages;

/** Server -> everyone: one chat line. Sender "Server" is used for announcements. */
public class ChatBroadcast extends Message {
    public static final String TYPE = "ChatBroadcast";

    private final String sender;
    private final String time; // HH:mm, stamped by the server
    private final String text;

    public ChatBroadcast(String sender, String time, String text) {
        super(TYPE);
        this.sender = sender;
        this.time = time;
        this.text = text;
    }

    public String getSender() {
        return sender;
    }

    public String getTime() {
        return time;
    }

    public String getText() {
        return text;
    }
}

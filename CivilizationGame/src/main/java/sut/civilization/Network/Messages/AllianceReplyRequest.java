package sut.civilization.Network.Messages;

/** Client -> server: accept or turn down a pending alliance offer from "from" (P3-40). */
public class AllianceReplyRequest extends Message {
    public static final String TYPE = "AllianceReplyRequest";

    private final String from;
    private final boolean accept;

    public AllianceReplyRequest(String from, boolean accept) {
        super(TYPE);
        this.from = from;
        this.accept = accept;
    }

    public String getFrom() {
        return from;
    }

    public boolean isAccept() {
        return accept;
    }
}

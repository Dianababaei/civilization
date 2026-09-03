package sut.civilization.Network.Messages;

/**
 * Client -> server: answer one trade offer by its id (P3-37).
 * The target player accepts/rejects it; the sender can also use this with
 * accept=false to cancel their own waiting offer.
 */
public class TradeReplyRequest extends Message {
    public static final String TYPE = "TradeReplyRequest";

    private final String offerId;
    private final boolean accept;

    public TradeReplyRequest(String offerId, boolean accept) {
        super(TYPE);
        this.offerId = offerId;
        this.accept = accept;
    }

    public String getOfferId() {
        return offerId;
    }

    public boolean isAccept() {
        return accept;
    }
}

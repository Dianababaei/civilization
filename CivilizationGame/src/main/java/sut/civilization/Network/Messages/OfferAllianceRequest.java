package sut.civilization.Network.Messages;

/** Client -> server: propose an alliance to another player; they must accept it (P3-40). */
public class OfferAllianceRequest extends Message {
    public static final String TYPE = "OfferAllianceRequest";

    private final String target;

    public OfferAllianceRequest(String target) {
        super(TYPE);
        this.target = target;
    }

    public String getTarget() {
        return target;
    }
}

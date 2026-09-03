package sut.civilization.Network.Messages;

/** Client -> server: one-sided break of an existing alliance, back to Neutral (P3-40). */
public class BreakAllianceRequest extends Message {
    public static final String TYPE = "BreakAllianceRequest";

    private final String target;

    public BreakAllianceRequest(String target) {
        super(TYPE);
        this.target = target;
    }

    public String getTarget() {
        return target;
    }
}

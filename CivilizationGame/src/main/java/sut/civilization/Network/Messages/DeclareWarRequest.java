package sut.civilization.Network.Messages;

/** Client -> server: one-sided war declaration against another player (P3-39). */
public class DeclareWarRequest extends Message {
    public static final String TYPE = "DeclareWarRequest";

    private final String target;

    public DeclareWarRequest(String target) {
        super(TYPE);
        this.target = target;
    }

    public String getTarget() {
        return target;
    }
}

package sut.civilization.Network.Messages;

/**
 * Server -> the requesting client: verdict of one action request. The text is
 * the controller's own message ("Unit can't reach there!", ...) shown to the
 * player unchanged.
 */
public class ActionResultResponse extends Message {
    public static final String TYPE = "ActionResultResponse";

    private final boolean ok;
    private final String text;

    public ActionResultResponse(boolean ok, String text) {
        super(TYPE);
        this.ok = ok;
        this.text = text;
    }

    public boolean isOk() {
        return ok;
    }

    public String getText() {
        return text;
    }
}

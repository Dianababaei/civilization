package sut.civilization.Network.Messages;

/**
 * Client -> server: a demo/cheat shortcut for evaluation (P3-58 allows demoing
 * rare scenarios with debug tooling; P3-48's rules apply: host-only, executed
 * by the SERVER on the authoritative game and announced to every player -
 * never applied locally).
 */
public class DebugActionRequest extends Message {
    public static final String TYPE = "DebugActionRequest";

    public static final String RESOURCES = "RESOURCES";           // +500 of everything for me
    public static final String TELEPORT_TO_ENEMY = "TELEPORT";    // my first military unit -> next to enemy TH
    public static final String WEAKEN_ENEMY_TH = "WEAKEN_TH";     // enemy Town Hall HP -> 15
    public static final String REFRESH_AP = "REFRESH_AP";         // my units' AP refilled
    public static final String CLEAR_HOSTILES = "CLEAR_HOSTILES"; // bears/barbarians/camps removed

    private final String action;

    public DebugActionRequest(String action) {
        super(TYPE);
        this.action = action;
    }

    public String getAction() {
        return action;
    }
}

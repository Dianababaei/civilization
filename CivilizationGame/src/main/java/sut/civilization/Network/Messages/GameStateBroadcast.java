package sut.civilization.Network.Messages;

import java.util.List;

/**
 * Server -> one client: that client's personal view of the game.
 *
 * stateBase64 is the serialized Game AFTER the server applied this player's
 * Fog of War (hidden tiles are stripped server-side - the packet physically
 * lacks what the player must not know). See docs/phase3-architecture.md ADR 2
 * for why the state rides as a blob while everything else is typed JSON.
 */
public class GameStateBroadcast extends Message {
    public static final String TYPE = "GameStateBroadcast";

    private final String stateBase64;
    private final String currentPlayer; // whose turn it is
    private final int turn;
    private final List<String> notifications; // end-of-round world report, may be empty

    public GameStateBroadcast(String stateBase64, String currentPlayer, int turn, List<String> notifications) {
        super(TYPE);
        this.stateBase64 = stateBase64;
        this.currentPlayer = currentPlayer;
        this.turn = turn;
        this.notifications = notifications;
    }

    public String getStateBase64() {
        return stateBase64;
    }

    public String getCurrentPlayer() {
        return currentPlayer;
    }

    public int getTurn() {
        return turn;
    }

    public List<String> getNotifications() {
        return notifications;
    }
}

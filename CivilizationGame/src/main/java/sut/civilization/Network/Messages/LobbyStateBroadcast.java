package sut.civilization.Network.Messages;

import java.util.List;

/** Server -> everyone: full lobby snapshot, pushed after every change. */
public class LobbyStateBroadcast extends Message {
    public static final String TYPE = "LobbyStateBroadcast";

    private final List<LobbyPlayerInfo> players;
    private final String chosenMap;
    private final List<String> maps;

    public LobbyStateBroadcast(List<LobbyPlayerInfo> players, String chosenMap, List<String> maps) {
        super(TYPE);
        this.players = players;
        this.chosenMap = chosenMap;
        this.maps = maps;
    }

    public List<LobbyPlayerInfo> getPlayers() {
        return players;
    }

    public String getChosenMap() {
        return chosenMap;
    }

    public List<String> getMaps() {
        return maps;
    }
}

package sut.civilization.Network.Messages;

/** One row of the lobby list as the server broadcasts it. Not a Message itself. */
public class LobbyPlayerInfo {
    private final String id;
    private final long createdAt; // when this player joined
    private final String name;
    private final boolean ready;
    private final boolean host;

    public LobbyPlayerInfo(String id, long createdAt, String name, boolean ready, boolean host) {
        this.id = id;
        this.createdAt = createdAt;
        this.name = name;
        this.ready = ready;
        this.host = host;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isHost() {
        return host;
    }
}

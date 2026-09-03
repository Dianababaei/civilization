package sut.civilization.Network.Messages;

import java.util.UUID;

/**
 * Base of everything that crosses the wire. The phase 3 doc requires every
 * core data entity to carry a unique id and a creation timestamp, so both
 * live here and every message inherits them.
 */
public abstract class Message {

    /** P3-51 bonus: the JWT the client attaches after login; null before it. */
    private String token;

    public String getToken() { return token; }

    public void setToken(String token) { this.token = token; }

    private final String type;
    private final String id;
    private final long createdAt;

    protected Message(String type) {
        this.type = type;
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

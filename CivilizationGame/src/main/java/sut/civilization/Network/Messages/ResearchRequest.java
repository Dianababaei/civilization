package sut.civilization.Network.Messages;

/**
 * Client -> server: queue a technology research in the Town Hall. The server
 * re-runs every phase 2 check (already researched, prerequisite, level,
 * resources, busy queue) and answers with the exact reason when it refuses.
 * With this, no game action is left running locally in multiplayer.
 */
public class ResearchRequest extends Message {
    public static final String TYPE = "ResearchRequest";

    private final String techName;

    public ResearchRequest(String techName) {
        super(TYPE);
        this.techName = techName;
    }

    public String getTechName() {
        return techName;
    }
}

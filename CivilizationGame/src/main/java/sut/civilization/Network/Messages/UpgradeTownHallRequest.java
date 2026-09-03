package sut.civilization.Network.Messages;

/**
 * Client -> server: queue the Town Hall upgrade (level 2 Settlement / level 3
 * Capital). Same rules as single player - resources, one production at a time -
 * validated by the server; the queue advances in the end-of-round world step.
 */
public class UpgradeTownHallRequest extends Message {
    public static final String TYPE = "UpgradeTownHallRequest";

    public UpgradeTownHallRequest() {
        super(TYPE);
    }
}

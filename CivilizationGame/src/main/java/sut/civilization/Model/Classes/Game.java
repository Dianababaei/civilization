package sut.civilization.Model.Classes;

import sut.civilization.Enums.Menus;

import javax.swing.*;
import java.awt.CardLayout;
import java.util.ArrayList;


public class Game implements java.io.Serializable {
    public transient static Game instance = new Game();

    public Land[][] map;
    // transient: the distance/path cache is huge and fully recomputable, so save
    // files skip it and SaveController rebuilds it right after a load
    public transient int[][] dist = new int[225][225];
    public transient String[][] path = new String[225][225];
    private ArrayList<User> users = new ArrayList<>();
    private transient JFrame mainWindow;
    private transient CardLayout cardLayout;
    private transient JPanel screenContainer;
    private transient SceneFactory sceneFactory;
    private User loggedInUser;
    // phase 2 (P2-66): dice, disasters etc. all roll from this one Random. It is NOT
    // transient on purpose - the spec says the RNG state must survive save/load, so
    // reloading a save can't reroll different random events
    private java.util.Random rng = new java.util.Random();
    // phase 2 bear attacks (P2-57): the den forest hex and the "calm" cooldown.
    // Kept on Game (not static) so they ride inside save files
    public Pair<Integer, Integer> bearDen = null;
    public int bearCalmUntilTurn = 0;
    private int turn = 1;
    // a number between 0 and players count in order to know witch user have to play
    private int subTurn = 0;
    private ArrayList<User> playersInGame = new ArrayList<>();

    // ── phase 3 PvP state (P3-01, P3-35..41) ─────────────────────────────────
    // kept on Game (not on the server session) so every snapshot carries it and
    // the clients can render diplomacy colors and the trade inbox from their copy.
    // All of these use lazy getters because phase-2 save files deserialize the
    // fields as null (same trick as getRng above)

    /** "alice|bob" (names sorted) -> Relation name. Missing pair = NEUTRAL. */
    private java.util.HashMap<String, sut.civilization.Model.ModulEnums.Relation> relations;
    /** Alliance offers waiting for an answer: proposer username -> target username. */
    private java.util.HashMap<String, String> allianceOffers;
    private ArrayList<TradeOffer> tradeOffers;
    /** Usernames whose last Town Hall was destroyed (P3-01). They stay in playersInGame so old snapshots resolve, but own nothing. */
    private ArrayList<String> eliminatedPlayers;
    /** Set once only one player is left standing - the game is over. */
    private String winner = null;

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public sut.civilization.Model.ModulEnums.Relation relationBetween(String a, String b) {
        if (relations == null) relations = new java.util.HashMap<>();
        return relations.getOrDefault(pairKey(a, b), sut.civilization.Model.ModulEnums.Relation.NEUTRAL);
    }

    public void setRelation(String a, String b, sut.civilization.Model.ModulEnums.Relation relation) {
        if (relations == null) relations = new java.util.HashMap<>();
        relations.put(pairKey(a, b), relation);
    }

    public java.util.HashMap<String, String> getAllianceOffers() {
        if (allianceOffers == null) allianceOffers = new java.util.HashMap<>();
        return allianceOffers;
    }

    public ArrayList<TradeOffer> getTradeOffers() {
        if (tradeOffers == null) tradeOffers = new ArrayList<>();
        return tradeOffers;
    }

    public TradeOffer findTradeOffer(String id) {
        for (TradeOffer offer : getTradeOffers())
            if (offer.id.equals(id)) return offer;
        return null;
    }

    public ArrayList<String> getEliminatedPlayers() {
        if (eliminatedPlayers == null) eliminatedPlayers = new ArrayList<>();
        return eliminatedPlayers;
    }

    public boolean isEliminated(String username) {
        return getEliminatedPlayers().contains(username);
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    // gives back the JPanel for a screen, set up once by the app entry point
    public interface SceneFactory {
        JPanel createScreen(Menus menu);
    }

    public JFrame getMainWindow() {
        return mainWindow;
    }

    // sets up the main window + CardLayout container, only called once when app starts
    public void initWindow(JFrame mainWindow, CardLayout cardLayout, JPanel screenContainer, SceneFactory sceneFactory) {
        this.mainWindow = mainWindow;
        this.cardLayout = cardLayout;
        this.screenContainer = screenContainer;
        this.sceneFactory = sceneFactory;
    }

    // phase 2 save/load: a deserialized Game has null window fields (transient), so it
    // borrows them from the instance that was alive before the load replaced it
    public void adoptWindowFrom(Game old) {
        this.mainWindow = old.mainWindow;
        this.cardLayout = old.cardLayout;
        this.screenContainer = old.screenContainer;
        this.sceneFactory = old.sceneFactory;
    }

    public java.util.Random getRng() {
        // saves from before this field existed deserialize it as null
        if (rng == null) rng = new java.util.Random();
        return rng;
    }

    /** Phase 3: called on snapshot COPIES only - the server's RNG state must never
     *  reach a client, or a smart client could predict every future die roll. */
    public void scrubRng() {
        rng = new java.util.Random();
    }

    public int getSubTurn() {
        return subTurn;
    }

    public void setSubTurn(int subTurn) {
        Game.instance.subTurn = subTurn;
    }

    public int getTurn() {
        return turn;
    }

    public void setTurn(int turn) {
        Game.instance.turn = turn;
    }

    public ArrayList<User> getPlayersInGame() {
        return playersInGame;
    }

    public void setPlayersInGame(ArrayList<User> playersInGame) {
        Game.instance.playersInGame = playersInGame;
    }

    public ArrayList<User> getUsers() {
        return users;
    }

    public void setUsers(ArrayList<User> users) {
        Game.instance.users = users;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    public void setLoggedInUser(User user) {
        Game.instance.loggedInUser = user;
    }

    public void setMap(Land[][] map) {
        Game.instance.map = map;
    }

    public void addUser(User user) {
        Game.instance.users.add(user);
    }

    // switches to the given screen, rebuilds it fresh every time (this is basically copying
    // how the old FXML version reloaded the scene root on every switch)
    public void changeScene(Menus menu) {
        JPanel screen = sceneFactory.createScreen(menu);
        String cardName = menu.name();
        screenContainer.add(screen, cardName);
        cardLayout.show(screenContainer, cardName);
        screenContainer.revalidate();
        screenContainer.repaint();
        screen.requestFocusInWindow();
    }
}

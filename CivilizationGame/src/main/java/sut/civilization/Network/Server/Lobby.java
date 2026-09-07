package sut.civilization.Network.Server;

import sut.civilization.Network.Messages.ChatBroadcast;
import sut.civilization.Network.Messages.GameStartingBroadcast;
import sut.civilization.Network.Messages.JoinResultResponse;
import sut.civilization.Network.Messages.LobbyPlayerInfo;
import sut.civilization.Network.Messages.LobbyStateBroadcast;
import sut.civilization.Network.Messages.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Server-side lobby state: the waiting room before the match.
 * Every public method is synchronized because each connected client talks to
 * this object from its own handler thread; the lobby list is the shared state
 * they must never race on. The host is simply the first player in the list,
 * so when the host leaves the next player inherits the role automatically.
 */
public class Lobby {
    public static final List<String> MAPS =
            Arrays.asList("Green Valley", "Twin Rivers", "Iron Ridge");
    public static final int MIN_PLAYERS = 2;

    private final List<ClientHandler> players = new ArrayList<>();
    private String chosenMap = MAPS.get(0);
    private boolean started = false;
    private GameSession session = null;

    // ── P3-51 bonus: password accounts + JWT ─────────────────────────────────
    // Sign up registers a username+password, login must match it. Accounts are
    // kept in a file (as SHA-256 hashes, never plain text) so they survive
    // server restarts - every "Host Game" spawns a fresh server process, and
    // without the file it would forget every password ever registered.
    // A successful login gets a signed token that every later request
    // must carry (ClientHandler checks it via checkToken)
    // not final: the test driver points this at a temp file so its fake test
    // users never end up registered in the real game's account book
    public static java.io.File ACCOUNTS_FILE = new java.io.File("mp_accounts.txt");
    private final java.util.Map<String, String> accounts = new java.util.HashMap<>();
    private final TokenService tokenService = new TokenService();

    public Lobby() {
        if (!ACCOUNTS_FILE.exists()) return;
        try (java.util.Scanner in = new java.util.Scanner(ACCOUNTS_FILE, "UTF-8")) {
            while (in.hasNextLine()) {
                String[] parts = in.nextLine().split(" ", 2);
                if (parts.length == 2) accounts.put(parts[0], parts[1]);
            }
        } catch (Exception e) {
            System.out.println("Could not read " + ACCOUNTS_FILE + ": " + e);
        }
    }

    private void saveAccounts() {
        try (java.io.PrintWriter out = new java.io.PrintWriter(ACCOUNTS_FILE, "UTF-8")) {
            for (java.util.Map.Entry<String, String> entry : accounts.entrySet())
                out.println(entry.getKey() + " " + entry.getValue());
        } catch (Exception e) {
            System.out.println("Could not write " + ACCOUNTS_FILE + ": " + e);
        }
    }

    private static String hash(String password) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 always exists on a JVM
        }
    }

    /** Null when the token authenticates this username, otherwise the complaint. */
    public String checkToken(String token, String username) {
        return tokenService.check(token, username);
    }

    /**
     * Answers the handler itself (accept or reject) so the JoinResultResponse
     * always reaches the client BEFORE the first lobby broadcast.
     */
    public synchronized void join(ClientHandler handler, String username, String password, String mode) {
        String reason = null;
        String name = username == null ? "" : username.trim();
        String passHash = hash(password == null ? "" : password);
        if (started) reason = "The game has already started, you can not join anymore.";
        else if (name.isEmpty()) reason = "Username can not be empty.";
        else {
            for (ClientHandler p : players)
                if (p.getUsername().equalsIgnoreCase(name)) {
                    reason = "The username \"" + name + "\" is already taken.";
                    break;
                }
            String knownHash = accounts.get(name.toLowerCase());
            if (reason == null) {
                if ("signup".equals(mode) && knownHash != null)
                    reason = "\"" + name + "\" is already registered - choose Login instead.";
                else if ("login".equals(mode) && knownHash == null)
                    reason = "There is no account called \"" + name + "\" - choose Sign up first.";
                else if (knownHash != null && !knownHash.equals(passHash))
                    reason = "Wrong password for \"" + name + "\".";
            }
        }
        if (reason != null) {
            handler.send(new JoinResultResponse(false, reason, null, false));
            return;
        }
        if (!accounts.containsKey(name.toLowerCase())) {
            accounts.put(name.toLowerCase(), passHash);
            saveAccounts();
        }
        handler.setUsername(name);
        players.add(handler);
        boolean host = isHost(handler);
        String token = tokenService.issue(name, host ? "host" : "player");
        handler.send(new JoinResultResponse(true, null, name, host, token));
        announce(name + " joined the lobby.");
        broadcastState();
    }

    /** Called by the handler thread when its socket dies or closes. */
    public synchronized void leave(ClientHandler handler) {
        if (players.remove(handler)) {
            announce(handler.getUsername() + " has disconnected.");
            if (session != null) {
                // mid-game drop: the session skips them so the match never freezes.
                // whatever goes wrong in there must never kill this handler thread
                // (the server surviving client crashes is a hard requirement)
                try {
                    session.playerLeft(handler);
                } catch (Exception e) {
                    System.out.println("Trouble handling " + handler.getUsername() + "'s exit: " + e);
                }
            } else {
                broadcastState();
            }
        }
        // a standalone server with nobody connected is useless: exit so the
        // port frees up and the next Host Game gets 5555 again instead of an
        // ever-climbing port list. Runs for rejected joins too (they never
        // enter the players list) - a wrong password must not leave a server
        if (GameServer.standalone && players.isEmpty()) {
            System.out.println("Nobody is connected any more - shutting down.");
            System.exit(0);
        }
    }

    public synchronized void setReady(ClientHandler handler, boolean ready) {
        handler.setReady(ready);
        broadcastState();
    }

    /** Returns null on success, otherwise the rejection reason. */
    public synchronized String chooseMap(ClientHandler handler, String mapName) {
        if (!isHost(handler)) return "Only the host can choose the map.";
        if (!MAPS.contains(mapName)) return "There is no map called \"" + mapName + "\".";
        chosenMap = mapName;
        broadcastState();
        return null;
    }

    /** Returns null on success, otherwise the rejection reason. */
    public synchronized String startGame(ClientHandler handler) {
        if (!isHost(handler)) return "Only the host can start the game.";
        if (players.size() < MIN_PLAYERS)
            return "At least " + MIN_PLAYERS + " players are needed to start.";
        for (ClientHandler p : players)
            if (!p.isReady()) return "Everyone must be Ready first (" + p.getUsername() + " is not).";
        started = true;
        broadcast(new GameStartingBroadcast(chosenMap));
        // phase 3 tier 2: build the authoritative game and push the first snapshots
        session = new GameSession(players, chosenMap);
        return null;
    }

    public synchronized GameSession getSession() {
        return session;
    }

    /** Returns null on success. Chat costs nothing and ignores turn order by design. */
    public synchronized String chat(ClientHandler handler, String text) {
        if (text == null || text.trim().isEmpty()) return "Empty message.";
        broadcast(new ChatBroadcast(handler.getUsername(), now(), text.trim()));
        return null;
    }

    public synchronized boolean isHost(ClientHandler handler) {
        return !players.isEmpty() && players.get(0) == handler;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    // ── push helpers ─────────────────────────────────────────────────────────

    private void broadcastState() {
        List<LobbyPlayerInfo> infos = new ArrayList<>();
        for (ClientHandler p : players)
            infos.add(new LobbyPlayerInfo(p.getPlayerId(), p.getJoinedAt(),
                    p.getUsername(), p.isReady(), isHost(p)));
        broadcast(new LobbyStateBroadcast(infos, chosenMap, MAPS));
    }

    private void announce(String text) {
        broadcast(new ChatBroadcast("Server", now(), text));
    }

    private void broadcast(Message message) {
        for (ClientHandler p : new ArrayList<>(players))
            p.send(message);
    }

    private String now() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }
}

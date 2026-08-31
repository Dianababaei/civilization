import sut.civilization.Network.Messages.*;
import sut.civilization.Network.Server.GameServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Headless driver for the phase 3 network layer (protocol + server + lobby + chat).
 * Talks to a real GameServer over real sockets - no Swing involved.
 * Run:
 *   javac -encoding UTF-8 -cp "target\classes;<gson.jar>" -d target\test-classes src\Test\java\Phase3Driver.java
 *   java -cp "target\classes;target\test-classes;<gson.jar>" Phase3Driver
 */
public class Phase3Driver {
    static int passed = 0, failed = 0;
    static final int PORT = 5599;

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** Minimal test client: raw TCP socket + the shared codec. */
    static class TestClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        TestClient() throws IOException {
            this(PORT);
        }

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            // generous: on a busy machine (several JVMs open) building the match
            // (map + 225x225 distance matrix) can push a reply past 8 seconds
            socket.setSoTimeout(20000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void send(Message m) {
            out.println(MessageCodec.encode(m));
        }

        /** Reads (and discards) pushed messages until one of the wanted type arrives. */
        Message expect(String type) throws IOException {
            for (int i = 0; i < 20; i++) {
                String line = in.readLine();
                if (line == null) throw new IOException("connection closed while waiting for " + type);
                Message m = MessageCodec.decode(line);
                if (m.getType().equals(type)) return m;
            }
            throw new IOException("never received " + type);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        // the round wrap runs the phase-2 world step, which autosaves - keep those
        // files away from the player's real save folder (same trick as Phase2Driver)
        sut.civilization.Controller.GameControllers.SaveController.saveDirectory =
                System.getProperty("java.io.tmpdir") + java.io.File.separator + "civ-phase3-driver-saves";

        // System.exit in the finally block: if a test dies mid-way, the server's
        // accept/handler threads must not keep a zombie JVM (and its port) alive,
        // poisoning every later run
        try {
            GameServer server = new GameServer(PORT);
            server.start();

            testCodec();
            testLobbyFlow();
            testHostMigration();
            testUdpHeartbeat();

            server.stopServer();

            testMapDeterminism();
            testGameSession();
            testPvp();
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL: driver crashed: " + t);
            t.printStackTrace();
        } finally {
            System.out.println("\nPhase3Driver: " + passed + " passed, " + failed + " failed");
            System.exit(failed > 0 ? 1 : 0);
        }
    }

    // ── protocol ─────────────────────────────────────────────────────────────

    static void testCodec() {
        ChatSendRequest original = new ChatSendRequest("hello world");
        String json = MessageCodec.encode(original);
        check("codec: JSON carries the type field", json.contains("\"type\":\"ChatSendRequest\""));
        check("codec: JSON is a single line", !json.contains("\n"));

        Message decoded = MessageCodec.decode(json);
        check("codec: round-trip restores the class", decoded instanceof ChatSendRequest);
        check("codec: round-trip keeps the payload", ((ChatSendRequest) decoded).getText().equals("hello world"));
        check("codec: entity has an id", decoded.getId() != null && !decoded.getId().isEmpty());
        check("codec: entity has createdAt", decoded.getCreatedAt() > 0);
        check("codec: id survives the wire unchanged", decoded.getId().equals(original.getId()));

        boolean threw = false;
        try {
            MessageCodec.decode("{\"type\":\"NoSuchThing\"}");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("codec: unknown type is rejected", threw);
    }

    // ── lobby, chat, validation, start ───────────────────────────────────────

    static void testLobbyFlow() throws Exception {
        TestClient alice = new TestClient();

        // must join before anything else
        alice.send(new ChatSendRequest("early"));
        ErrorResponse early = (ErrorResponse) alice.expect(ErrorResponse.TYPE);
        check("lobby: requests before joining are rejected", early.getReason().contains("Join"));

        alice.send(new JoinLobbyRequest("alice"));
        JoinResultResponse aliceJoin = (JoinResultResponse) alice.expect(JoinResultResponse.TYPE);
        check("lobby: first player joins ok", aliceJoin.isOk());
        check("lobby: first player is the host", aliceJoin.isHost());
        LobbyStateBroadcast state = (LobbyStateBroadcast) alice.expect(LobbyStateBroadcast.TYPE);
        check("lobby: state lists the one player", state.getPlayers().size() == 1);
        check("lobby: three maps are offered", state.getMaps().size() == 3);
        LobbyPlayerInfo info = state.getPlayers().get(0);
        check("lobby: player entity has id + createdAt", info.getId() != null && info.getCreatedAt() > 0);

        // duplicate + empty usernames
        try (TestClient dup = new TestClient()) {
            dup.send(new JoinLobbyRequest("ALICE"));
            JoinResultResponse r = (JoinResultResponse) dup.expect(JoinResultResponse.TYPE);
            check("lobby: duplicate username rejected (case-insensitive)", !r.isOk() && r.getReason().contains("taken"));
        }
        try (TestClient empty = new TestClient()) {
            empty.send(new JoinLobbyRequest("   "));
            JoinResultResponse r = (JoinResultResponse) empty.expect(JoinResultResponse.TYPE);
            check("lobby: empty username rejected", !r.isOk());
        }

        TestClient bob = new TestClient();
        bob.send(new JoinLobbyRequest("bob"));
        JoinResultResponse bobJoin = (JoinResultResponse) bob.expect(JoinResultResponse.TYPE);
        check("lobby: second player is not host", bobJoin.isOk() && !bobJoin.isHost());
        state = (LobbyStateBroadcast) alice.expect(LobbyStateBroadcast.TYPE);
        check("lobby: join is pushed to everyone", state.getPlayers().size() == 2);

        // ready state propagates
        bob.send(new SetReadyRequest(true));
        state = (LobbyStateBroadcast) alice.expect(LobbyStateBroadcast.TYPE);
        boolean bobReady = false;
        for (LobbyPlayerInfo p : state.getPlayers())
            if (p.getName().equals("bob")) bobReady = p.isReady();
        check("lobby: ready toggle is pushed to everyone", bobReady);

        // map choice is host-only and validated
        bob.send(new ChooseMapRequest("Twin Rivers"));
        ErrorResponse err = (ErrorResponse) bob.expect(ErrorResponse.TYPE);
        check("lobby: non-host can not choose the map", err.getReason().contains("host"));
        alice.send(new ChooseMapRequest("Atlantis"));
        err = (ErrorResponse) alice.expect(ErrorResponse.TYPE);
        check("lobby: unknown map rejected", err.getReason().contains("no map"));
        alice.send(new ChooseMapRequest("Twin Rivers"));
        state = (LobbyStateBroadcast) bob.expect(LobbyStateBroadcast.TYPE);
        check("lobby: host's map choice is pushed", state.getChosenMap().equals("Twin Rivers"));

        // chat
        bob.send(new ChatSendRequest("good luck everyone"));
        ChatBroadcast chat = (ChatBroadcast) alice.expect(ChatBroadcast.TYPE);
        check("chat: everyone gets the message", chat.getText().equals("good luck everyone"));
        check("chat: sender name attached", chat.getSender().equals("bob"));
        check("chat: server stamps HH:mm time", chat.getTime().matches("\\d\\d:\\d\\d"));

        // disconnect notice
        TestClient carol = new TestClient();
        carol.send(new JoinLobbyRequest("carol"));
        carol.expect(JoinResultResponse.TYPE);
        alice.expect(LobbyStateBroadcast.TYPE);
        bob.expect(LobbyStateBroadcast.TYPE); // consume carol's join push on bob too
        carol.close();
        state = (LobbyStateBroadcast) alice.expect(LobbyStateBroadcast.TYPE);
        check("disconnect: player removed from pushed state", state.getPlayers().size() == 2);
        // the announcement travels as a Server chat line right before the state push;
        // read bob's copy since alice's was consumed by expect() above
        ChatBroadcast note = (ChatBroadcast) bob.expect(ChatBroadcast.TYPE);
        boolean sawNotice = note.getSender().equals("Server") && note.getText().contains("carol")
                && note.getText().contains("disconnected");
        check("disconnect: 'has disconnected' announced", sawNotice);
        bob.expect(LobbyStateBroadcast.TYPE);

        // start-game validation chain
        bob.send(new StartGameRequest());
        err = (ErrorResponse) bob.expect(ErrorResponse.TYPE);
        check("start: non-host can not start", err.getReason().contains("host"));
        alice.send(new StartGameRequest());
        err = (ErrorResponse) alice.expect(ErrorResponse.TYPE);
        check("start: blocked while someone is not ready", err.getReason().contains("Ready"));
        alice.send(new SetReadyRequest(true));
        alice.expect(LobbyStateBroadcast.TYPE);
        alice.send(new StartGameRequest());
        GameStartingBroadcast startA = (GameStartingBroadcast) alice.expect(GameStartingBroadcast.TYPE);
        GameStartingBroadcast startB = (GameStartingBroadcast) bob.expect(GameStartingBroadcast.TYPE);
        check("start: everyone is told the game starts", startA != null && startB != null);
        check("start: chosen map travels with it", startA.getMapName().equals("Twin Rivers"));

        // late joiner after start
        try (TestClient late = new TestClient()) {
            late.send(new JoinLobbyRequest("dave"));
            JoinResultResponse r = (JoinResultResponse) late.expect(JoinResultResponse.TYPE);
            check("start: joining a started game is rejected", !r.isOk() && r.getReason().contains("started"));
        }

        alice.close();
        bob.close();
        // this lobby's game session shares the JVM-wide Game.instance with the
        // sessions of the later tests (one JVM = one authoritative game; real
        // servers are separate processes) - let its disconnect handling finish
        // completely before anything else touches the singleton
        Thread.sleep(800);
    }

    // ── host leaves -> next player inherits the role ─────────────────────────

    static void testHostMigration() throws Exception {
        GameServer server2 = new GameServer(PORT + 1);
        server2.start();
        try {
            // TestClient is bound to PORT, so open these two sockets by hand on PORT+1
            try (Socket s1 = new Socket("127.0.0.1", PORT + 1);
                 Socket s2 = new Socket("127.0.0.1", PORT + 1)) {
                s1.setSoTimeout(3000);
                s2.setSoTimeout(3000);
                PrintWriter out1 = new PrintWriter(new OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in2 = new BufferedReader(new InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out2 = new PrintWriter(new OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8), true);

                out1.println(MessageCodec.encode(new JoinLobbyRequest("dana")));
                Thread.sleep(300);
                out2.println(MessageCodec.encode(new JoinLobbyRequest("erin")));
                Thread.sleep(300);
                s1.close(); // host leaves

                boolean erinIsHost = false;
                for (int i = 0; i < 20; i++) {
                    Message m = MessageCodec.decode(in2.readLine());
                    if (m instanceof LobbyStateBroadcast) {
                        LobbyStateBroadcast st = (LobbyStateBroadcast) m;
                        if (st.getPlayers().size() == 1)
                            erinIsHost = st.getPlayers().get(0).getName().equals("erin")
                                    && st.getPlayers().get(0).isHost();
                    }
                    if (erinIsHost) break;
                }
                check("host migration: remaining player becomes host", erinIsHost);
            }
        } finally {
            server2.stopServer();
        }
    }

    // ── phase 3 tier 2: named maps are deterministic ─────────────────────────

    static java.util.ArrayList<sut.civilization.Model.Classes.User> twoUsers() {
        java.util.ArrayList<sut.civilization.Model.Classes.User> users = new java.util.ArrayList<>();
        for (String name : new String[]{"alice", "bob"}) {
            sut.civilization.Model.Classes.User u =
                    new sut.civilization.Model.Classes.User(name, "", name);
            u.setNation(new sut.civilization.Model.Classes.Nation());
            users.add(u);
        }
        return users;
    }

    static void testMapDeterminism() {
        sut.civilization.Model.Classes.Land[][] first =
                sut.civilization.Controller.GameControllers.LandController.mapInitializerMultiplayer("Twin Rivers", twoUsers());
        sut.civilization.Model.Classes.Land[][] second =
                sut.civilization.Controller.GameControllers.LandController.mapInitializerMultiplayer("Twin Rivers", twoUsers());
        sut.civilization.Model.Classes.Land[][] other =
                sut.civilization.Controller.GameControllers.LandController.mapInitializerMultiplayer("Iron Ridge", twoUsers());

        boolean sameNameSame = true, otherNameDiffers = false;
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                if (first[i][j].getLandType() != second[i][j].getLandType()) sameNameSame = false;
                if (first[i][j].getLandType() != other[i][j].getLandType()) otherNameDiffers = true;
            }
        }
        check("maps: same name -> identical terrain", sameNameSame);
        check("maps: different name -> different terrain", otherNameDiffers);

        // P3-04 fairness: EVERY named map must give EVERY spawn open ground plus
        // stone and iron within reach - no corner crippled by seed luck
        boolean allFair = true;
        for (String name : new String[]{"Green Valley", "Twin Rivers", "Iron Ridge"}) {
            sut.civilization.Model.Classes.Land[][] map =
                    sut.civilization.Controller.GameControllers.LandController.mapInitializerMultiplayer(name, twoUsers());
            for (int[] s : new int[][]{{3, 3}, {11, 11}}) {
                sut.civilization.Model.Classes.Pair<Integer, Integer> spawn =
                        new sut.civilization.Model.Classes.Pair<>(s[0], s[1]);
                int open = 0;
                boolean stone = false, iron = false;
                for (sut.civilization.Model.Classes.Pair<Integer, Integer> hex :
                        sut.civilization.Controller.GameControllers.DisasterController.hexesInRadius(spawn, 3)) {
                    if (!sut.civilization.Model.Classes.Pair.isValid(hex)) continue;
                    sut.civilization.Model.Classes.Land tile = map[hex.x][hex.y];
                    if (tile.getLandType() == sut.civilization.Model.ModulEnums.LandType.MOUNTAIN) {
                        stone = true;
                        if (tile.hasIron()) iron = true;
                    }
                }
                for (sut.civilization.Model.Classes.Pair<Integer, Integer> hex :
                        sut.civilization.Controller.GameControllers.DisasterController.hexesInRadius(spawn, 2)) {
                    if (!sut.civilization.Model.Classes.Pair.isValid(hex) || hex.equals(spawn)) continue;
                    sut.civilization.Model.ModulEnums.LandType lt = map[hex.x][hex.y].getLandType();
                    if (lt == sut.civilization.Model.ModulEnums.LandType.PLAIN
                            || lt == sut.civilization.Model.ModulEnums.LandType.MEADOW
                            || lt == sut.civilization.Model.ModulEnums.LandType.FOREST) open++;
                }
                if (!stone || !iron || open < 6) {
                    allFair = false;
                    System.out.println("  unfair: " + name + " spawn (" + s[0] + "," + s[1]
                            + ") stone=" + stone + " iron=" + iron + " open=" + open);
                }
            }
        }
        check("maps: every spawn of every map has stone + iron in reach and open ground (P3-04)", allFair);
    }

    // ── phase 3 tier 2: full match flow over the network ─────────────────────

    static sut.civilization.Model.Classes.Game decodeState(GameStateBroadcast broadcast) throws Exception {
        byte[] bytes = java.util.Base64.getDecoder().decode(broadcast.getStateBase64());
        try (java.io.ObjectInputStream in =
                     new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return (sut.civilization.Model.Classes.Game) in.readObject();
        }
    }

    static sut.civilization.Model.Classes.User findUser(sut.civilization.Model.Classes.Game game, String name) {
        for (sut.civilization.Model.Classes.User u : game.getPlayersInGame())
            if (u.getUsername().equals(name)) return u;
        return null;
    }

    static void testGameSession() throws Exception {
        GameServer server = new GameServer(PORT + 2);
        server.start();
        TestClient alice = new TestClient(PORT + 2);
        TestClient bob = new TestClient(PORT + 2);
        try {
            alice.send(new JoinLobbyRequest("alice"));
            alice.expect(JoinResultResponse.TYPE);
            bob.send(new JoinLobbyRequest("bob"));
            bob.expect(JoinResultResponse.TYPE);
            alice.send(new ChooseMapRequest("Twin Rivers"));
            alice.send(new SetReadyRequest(true));
            bob.send(new SetReadyRequest(true));
            Thread.sleep(300); // let the lobby broadcasts flush
            alice.send(new StartGameRequest());

            GameStateBroadcast stateA = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            GameStateBroadcast stateB = (GameStateBroadcast) bob.expect(GameStateBroadcast.TYPE);
            check("game: first player opens the match", stateA.getCurrentPlayer().equals("alice"));
            check("game: starts at turn 1", stateA.getTurn() == 1);

            sut.civilization.Model.Classes.Game gameA = decodeState(stateA);
            sut.civilization.Model.Classes.Game gameB = decodeState(stateB);
            check("game: both players in the state", gameA.getPlayersInGame().size() == 2);

            sut.civilization.Model.Classes.User meA = findUser(gameA, "alice");
            check("game: alice has her town hall at her spawn",
                    meA != null && meA.getNation().getTownHall() != null
                            && meA.getNation().getTownHall().getMainLand().getI() == 3
                            && meA.getNation().getTownHall().getMainLand().getJ() == 3);
            // spec flow: only civil units at spawn - armies must be TRAINED
            check("game: alice starts with 5 civil units, no military",
                    meA.getNation().getUnits().size() == 5 && meA.getNation().getMilitaryUnitCount() == 0);

            // fog of war: bob's corner must be a black hole in alice's packet
            check("fog: bob's spawn tile is fogged for alice", gameA.map[11][11].getVisibility() == 0);
            sut.civilization.Model.Classes.User bobInA = findUser(gameA, "bob");
            check("fog: bob's unit list is stripped from alice's packet",
                    bobInA.getNation().getUnits().isEmpty());
            check("fog: bob's town hall is stripped from alice's packet",
                    bobInA.getNation().getTownHall() == null);
            check("fog: bob's territory is stripped from alice's packet",
                    bobInA.getNation().getTerritory().isEmpty());
            int bobUnitsOnAliceMap = 0;
            for (int i = 0; i < 15; i++)
                for (int j = 0; j < 15; j++)
                    if (gameA.map[i][j].getCivilizedUnit() != null
                            && gameA.map[i][j].getCivilizedUnit().getOwnerNation() == bobInA.getNation())
                        bobUnitsOnAliceMap++;
            check("fog: no bob unit rides on any tile of alice's packet", bobUnitsOnAliceMap == 0);
            check("fog: bob sees his own spawn", gameB.map[11][11].getVisibility() == 2);

            // never-seen tiles must not even leak their TERRAIN (they all travel as
            // an identical bare plain) or who else has seen them
            boolean terrainLeaks = false, seerLeaks = false;
            sut.civilization.Model.Classes.Nation bobNationInA = bobInA.getNation();
            for (int i = 0; i < 15; i++) {
                for (int j = 0; j < 15; j++) {
                    sut.civilization.Model.Classes.Land tile = gameA.map[i][j];
                    if (tile.getVisibility() == 0
                            && (tile.getLandType() != sut.civilization.Model.ModulEnums.LandType.PLAIN
                            || tile.getLandFeature() != null || tile.hasRoad() || tile.isVolcano()))
                        terrainLeaks = true;
                    if (tile.getSeerNations().contains(bobNationInA)
                            || tile.getExplorerSeenNations().contains(bobNationInA))
                        seerLeaks = true;
                }
            }
            check("fog: fogged tiles carry no terrain data (uniform plains)", !terrainLeaks);
            check("fog: no tile reveals what bob has explored", !seerLeaks);

            // chat keeps working mid-game, from anyone, regardless of whose turn it is
            bob.send(new ChatSendRequest("gg so far"));
            ChatBroadcast midGameChat = (ChatBroadcast) alice.expect(ChatBroadcast.TYPE);
            check("chat: works during the game and reaches the other player",
                    midGameChat.getSender().equals("bob") && midGameChat.getText().equals("gg so far"));
            bob.expect(ChatBroadcast.TYPE); // the sender sees their own message too

            // turn order is enforced by the server
            bob.send(new MoveUnitRequest(10, 10, 10, 11));
            ActionResultResponse denied = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("turns: acting out of turn is rejected", !denied.isOk() && denied.getText().contains("not your turn"));

            // ── training through the Town Hall (spec flow: build your army) ──
            bob.send(new TrainUnitRequest("Swordsman"));
            denied = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("train: out of turn is rejected", !denied.isOk() && denied.getText().contains("not your turn"));

            alice.send(new TrainUnitRequest("Archer"));
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("train: Archer refused at Town Hall level 1 with the reason",
                    !denied.isOk() && denied.getText().contains("level 2"));

            alice.send(new TrainUnitRequest("Swordsman"));
            ActionResultResponse trained = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("train: Swordsman is queued (level 1, resources suffice)",
                    trained.isOk() && trained.getText().contains("queue"));
            GameStateBroadcast afterTrain = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            check("train: the queue rides the snapshot",
                    findUser(decodeState(afterTrain), "alice").getNation().getTownHall().hasQueuedProduction());
            bob.expect(GameStateBroadcast.TYPE);

            alice.send(new TrainUnitRequest("Swordsman"));
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("train: a busy Town Hall refuses a second order",
                    !denied.isOk() && denied.getText().contains("busy"));

            // moving someone else's (or a missing) unit is rejected
            alice.send(new MoveUnitRequest(11, 11, 11, 10));
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("turns: moving a unit you can not see is rejected", !denied.isOk());

            // a real move: alice walks her explorer (spawned at 2,2) to some neighbor
            boolean moved = false;
            for (int k = 0; k < 6 && !moved; k++) {
                sut.civilization.Model.Classes.Pair<Integer, Integer> dest =
                        sut.civilization.Controller.GameControllers.LandController.getNeighborIndex(
                                new sut.civilization.Model.Classes.Pair<>(2, 2), k);
                if (!sut.civilization.Model.Classes.Pair.isValid(dest)) continue;
                alice.send(new MoveUnitRequest(2, 2, dest.x, dest.y));
                ActionResultResponse result = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
                if (result.isOk()) {
                    moved = true;
                    GameStateBroadcast after = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
                    sut.civilization.Model.Classes.Game gameAfter = decodeState(after);
                    check("move: the unit left its old hex", gameAfter.map[2][2].getCivilizedUnit() == null);
                    check("move: alice still owns her 5 units",
                            findUser(gameAfter, "alice").getNation().getUnits().size() == 5);
                    bob.expect(GameStateBroadcast.TYPE); // bob is updated too
                }
            }
            check("move: at least one neighbor was walkable", moved);

            // end of turn: alice -> bob -> (round wraps, world advances to turn 2)
            alice.send(new EndTurnRequest());
            ActionResultResponse passed1 = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("turns: alice passes to bob", passed1.isOk() && passed1.getText().contains("bob"));
            GameStateBroadcast bobsTurn = (GameStateBroadcast) bob.expect(GameStateBroadcast.TYPE);
            check("turns: broadcast says it is bob's turn", bobsTurn.getCurrentPlayer().equals("bob"));
            alice.expect(GameStateBroadcast.TYPE);

            bob.send(new EndTurnRequest());
            bob.expect(ActionResultResponse.TYPE);
            GameStateBroadcast round2 = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            check("turns: full round advances the world to turn 2", round2.getTurn() == 2);
            check("turns: back to alice", round2.getCurrentPlayer().equals("alice"));
            bob.expect(GameStateBroadcast.TYPE);

            // mid-game disconnect: bob drops, the match must carry on for alice
            bob.close();
            ChatBroadcast note = (ChatBroadcast) alice.expect(ChatBroadcast.TYPE);
            check("drop: bob's exit is announced", note.getText().contains("bob") && note.getText().contains("disconnected"));
            alice.expect(GameStateBroadcast.TYPE);
            alice.send(new EndTurnRequest());
            alice.expect(ActionResultResponse.TYPE);
            GameStateBroadcast alone = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            check("drop: game continues solo, world reaches turn 3", alone.getTurn() == 3);
            // the Swordsman queued on turn 1 (2 turns to train) is a real unit by now
            sut.civilization.Model.Classes.User aliceAtEnd = findUser(decodeState(alone), "alice");
            check("train: the Swordsman finished on the server and spawned near the Town Hall",
                    aliceAtEnd.getNation().getMilitaryUnitCount() == 1
                            && aliceAtEnd.getNation().getUnits().size() == 6);
            // private news (P3-19 for notifications): the owner gets their note as
            // plain text, and no internal "@name::" tag ever reaches a client
            boolean trainNote = false, tagLeak = false;
            for (String n : alone.getNotifications()) {
                if (n.contains("trained and ready")) trainNote = true;
                if (n.startsWith("@")) tagLeak = true;
            }
            check("notify: the training note reaches its owner", trainNote);
            check("notify: no owner tags leak into the client's report", !tagLeak);
        } finally {
            try {
                alice.close();
            } catch (Exception ignored) {
            }
            try {
                bob.close();
            } catch (Exception ignored) {
            }
            server.stopServer();
        }
    }

    // ── phase 3 tier 3: PvP combat, diplomacy, trade, win condition ──────────

    /** A free, sane hex next to `center` for planting a test unit on the server's map. */
    static sut.civilization.Model.Classes.Pair<Integer, Integer> freeNeighbor(
            sut.civilization.Model.Classes.Pair<Integer, Integer> center) {
        for (int k = 0; k < 6; k++) {
            sut.civilization.Model.Classes.Pair<Integer, Integer> hex =
                    sut.civilization.Controller.GameControllers.LandController.getNeighborIndex(center, k);
            if (!sut.civilization.Model.Classes.Pair.isValid(hex)) continue;
            sut.civilization.Model.Classes.Land tile = sut.civilization.Model.Classes.Game.instance.map[hex.x][hex.y];
            if (tile.getCivilizedUnit() != null || tile.getEnemy() != null) continue;
            if (tile.getLandType() == sut.civilization.Model.ModulEnums.LandType.MOUNTAIN_RANGE) continue;
            return hex;
        }
        return null;
    }

    /** Plants a fresh unit of the given type on the server's authoritative game (test rig:
     *  the spawns are far apart, walking a sword across the map would take dozens of turns). */
    static sut.civilization.Model.Classes.CivilizedUnit plantUnit(
            sut.civilization.Model.ModulEnums.CivilizedUnitType type,
            sut.civilization.Model.Classes.Nation nation,
            sut.civilization.Model.Classes.Pair<Integer, Integer> loc) {
        sut.civilization.Model.Classes.CivilizedUnit unit =
                new sut.civilization.Model.Classes.CivilizedUnit(type, nation, loc);
        sut.civilization.Model.Classes.Game.instance.map[loc.x][loc.y].setCivilizedUnit(unit);
        nation.getUnits().add(unit);
        return unit;
    }

    static void testPvp() throws Exception {
        Thread.sleep(800); // let the previous session's teardown settle (shared Game.instance in this JVM)
        GameServer server = new GameServer(PORT + 3);
        server.start();
        TestClient alice = new TestClient(PORT + 3);
        TestClient bob = new TestClient(PORT + 3);
        try {
            alice.send(new JoinLobbyRequest("alice"));
            alice.expect(JoinResultResponse.TYPE);
            bob.send(new JoinLobbyRequest("bob"));
            bob.expect(JoinResultResponse.TYPE);
            alice.send(new SetReadyRequest(true));
            bob.send(new SetReadyRequest(true));
            Thread.sleep(300);
            alice.send(new StartGameRequest());
            alice.expect(GameStateBroadcast.TYPE);
            bob.expect(GameStateBroadcast.TYPE);

            // ── rig the board directly on the server (same JVM) ──
            sut.civilization.Model.Classes.Game game = sut.civilization.Model.Classes.Game.instance;
            sut.civilization.Model.Classes.User aliceUser = null, bobUser = null;
            for (sut.civilization.Model.Classes.User u : game.getPlayersInGame()) {
                if (u.getUsername().equals("alice")) aliceUser = u;
                if (u.getUsername().equals("bob")) bobUser = u;
            }
            sut.civilization.Model.Classes.CivilizedUnit bobWorker = null;
            for (sut.civilization.Model.Classes.Unit u : bobUser.getNation().getUnits())
                if (u instanceof sut.civilization.Model.Classes.CivilizedUnit
                        && ((sut.civilization.Model.Classes.CivilizedUnit) u).getCivilizedUnitType()
                        == sut.civilization.Model.ModulEnums.CivilizedUnitType.WORKER)
                    bobWorker = (sut.civilization.Model.Classes.CivilizedUnit) u;
            sut.civilization.Model.Classes.Pair<Integer, Integer> thHex =
                    new sut.civilization.Model.Classes.Pair<>(11, 11);
            sut.civilization.Model.Classes.Pair<Integer, Integer> sword1Hex = freeNeighbor(bobWorker.getLocation());
            sut.civilization.Model.Classes.CivilizedUnit sword1 = plantUnit(
                    sut.civilization.Model.ModulEnums.CivilizedUnitType.SWORDSMAN, aliceUser.getNation(), sword1Hex);
            sut.civilization.Model.Classes.Pair<Integer, Integer> sword2Hex = freeNeighbor(thHex);
            sut.civilization.Model.Classes.CivilizedUnit sword2 = plantUnit(
                    sut.civilization.Model.ModulEnums.CivilizedUnitType.SWORDSMAN, aliceUser.getNation(), sword2Hex);
            check("pvp rig: found spots next to bob's worker and town hall", sword1Hex != null && sword2Hex != null);

            // ── flat structure damage table is exactly the phase 3 spec (P3-33) ──
            check("pvp: structure damage Swordsman 10 / Archer 6 / Cavalry 9",
                    sut.civilization.Controller.GameControllers.CombatController.structureDamageOf(
                            sut.civilization.Model.ModulEnums.CivilizedUnitType.SWORDSMAN) == 10
                            && sut.civilization.Controller.GameControllers.CombatController.structureDamageOf(
                            sut.civilization.Model.ModulEnums.CivilizedUnitType.ARCHER) == 6
                            && sut.civilization.Controller.GameControllers.CombatController.structureDamageOf(
                            sut.civilization.Model.ModulEnums.CivilizedUnitType.CAVALRY) == 9);

            // ── validation rules, straight on the controller ──
            check("pvp: civil units can never attack",
                    sut.civilization.Controller.GameControllers.CombatController.checkCanAttackHexPvp(
                            bobWorker, sword1Hex) != null);
            check("pvp: out-of-range target rejected",
                    sut.civilization.Controller.GameControllers.CombatController.checkCanAttackHexPvp(
                            sword2, new sut.civilization.Model.Classes.Pair<>(0, 0)) != null);

            // ── diplomacy gates the attack (P3-38): neutral -> no shooting ──
            alice.send(new AttackRequest(sword2Hex.x, sword2Hex.y, 11, 11));
            ActionResultResponse denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("diplomacy: attacking while Neutral is rejected with a clear reason",
                    !denied.isOk() && denied.getText().contains("not at war"));

            // ── trade: validation + lock (P3-35/36) ──
            alice.send(new TradeOfferRequest("bob", 0, 0, 0, 5, 1, 0, 0, 0)); // 5 iron - alice has 0
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("trade: offering resources you lack is rejected", !denied.isOk());

            alice.send(new TradeOfferRequest("bob", 0, 5, 0, 0, 0, 0, 3, 0)); // 5 wood for 3 stone
            ActionResultResponse ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("trade: a valid offer is accepted and locked", ok.isOk() && ok.getText().contains("locked"));
            GameStateBroadcast afterOffer = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            sut.civilization.Model.Classes.Game gameAfterOffer = decodeState(afterOffer);
            check("trade: the offered wood is locked out of alice's balance (20 -> 15)",
                    findUser(gameAfterOffer, "alice").getNation().getWood().getBalance() == 15);
            check("trade: the offer rides the snapshot with id + createdAt",
                    gameAfterOffer.getTradeOffers().size() == 1
                            && gameAfterOffer.getTradeOffers().get(0).id != null
                            && gameAfterOffer.getTradeOffers().get(0).createdAt > 0);
            bob.expect(GameStateBroadcast.TYPE);

            alice.send(new TradeOfferRequest("bob", 0, 2, 0, 0, 0, 0, 1, 0)); // second offer: 2 wood for 1 stone
            alice.expect(ActionResultResponse.TYPE);
            GameStateBroadcast afterOffer2 = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            sut.civilization.Model.Classes.Game gameAfterOffer2 = decodeState(afterOffer2);
            String offer1Id = null, offer2Id = null;
            for (sut.civilization.Model.Classes.TradeOffer offer : gameAfterOffer2.getTradeOffers()) {
                if (offer.giveWood == 5) offer1Id = offer.id;
                if (offer.giveWood == 2) offer2Id = offer.id;
            }
            check("trade: both offers are in the inbox", offer1Id != null && offer2Id != null);
            bob.expect(GameStateBroadcast.TYPE);

            // answering the inbox happens in YOUR turn (P3-37) - bob must wait
            bob.send(new TradeReplyRequest(offer1Id, true));
            denied = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("trade: answering out of turn is rejected", !denied.isOk() && denied.getText().contains("not your turn"));

            // ── alliance: offer -> accept -> shared vision (P3-40) ──
            bob.send(new OfferAllianceRequest("alice"));
            ok = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("diplomacy: alliance offer goes out", ok.isOk());
            bob.expect(GameStateBroadcast.TYPE); // the offer itself triggers a state push too
            alice.send(new AllianceReplyRequest("bob", true));
            ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("diplomacy: accepting seals the alliance", ok.isOk());
            GameStateBroadcast allied = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            sut.civilization.Model.Classes.Game gameAllied = decodeState(allied);
            check("diplomacy: snapshot says the two are allies",
                    gameAllied.relationBetween("alice", "bob") == sut.civilization.Model.ModulEnums.Relation.ALLY);
            check("alliance: shared vision - bob's spawn is now visible to alice",
                    gameAllied.map[11][11].getVisibility() == 2);
            check("alliance: bob's units are no longer stripped from alice's packet",
                    !findUser(gameAllied, "bob").getNation().getUnits().isEmpty());
            bob.expect(GameStateBroadcast.TYPE);

            alice.send(new AttackRequest(sword2Hex.x, sword2Hex.y, 11, 11));
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("diplomacy: allies can not attack each other", !denied.isOk() && denied.getText().contains("not at war"));

            // ── war declaration flips the pair to Enemy with a global alarm (P3-39) ──
            alice.send(new DeclareWarRequest("bob"));
            ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("diplomacy: war declaration accepted", ok.isOk());
            GameStateBroadcast atWar = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            boolean warAnnounced = false;
            for (String n : atWar.getNotifications()) if (n.contains("DECLARED WAR")) warAnnounced = true;
            check("diplomacy: everyone gets the global war alarm", warAnnounced);
            sut.civilization.Model.Classes.Game gameAtWar = decodeState(atWar);
            check("diplomacy: snapshot says Enemy now",
                    gameAtWar.relationBetween("alice", "bob") == sut.civilization.Model.ModulEnums.Relation.ENEMY);
            check("war: bob's units are stripped from alice's packet again",
                    findUser(gameAtWar, "bob").getNation().getUnits().isEmpty());
            bob.expect(GameStateBroadcast.TYPE);

            // ── unit vs unit dice battle (P3-32): 1 die each, server rolls ──
            alice.send(new AttackRequest(sword1Hex.x, sword1Hex.y, bobWorker.getLocation().x, bobWorker.getLocation().y));
            ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("pvp: attack on an enemy unit goes through", ok.isOk());
            alice.expect(GameStateBroadcast.TYPE);
            BattleReportMessage aliceReport = (BattleReportMessage) alice.expect(BattleReportMessage.TYPE);
            check("pvp: dice battle rolled one die per participating unit",
                    aliceReport.getAttackerDice().length == 1 && aliceReport.getDefenderDice().length == 1);
            bob.expect(GameStateBroadcast.TYPE);

            // ── war report reaches the victim when THEIR turn starts (P3-34) ──
            alice.send(new EndTurnRequest());
            alice.expect(ActionResultResponse.TYPE);
            GameStateBroadcast bobsTurn = (GameStateBroadcast) bob.expect(GameStateBroadcast.TYPE);
            check("pvp: turn passes to the victim", bobsTurn.getCurrentPlayer().equals("bob"));
            BattleReportMessage warReport = (BattleReportMessage) bob.expect(BattleReportMessage.TYPE);
            check("pvp: the victim's war report arrives at the start of their turn",
                    warReport.getTitle().contains("attacks"));
            alice.expect(GameStateBroadcast.TYPE);

            // bob's turn: he can't command alice's sword (sword2 - it always survives,
            // structure attacks get no counterattack), and he answers his inbox
            bob.send(new AttackRequest(sword2Hex.x, sword2Hex.y, 11, 11));
            denied = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("pvp: commanding an opponent's unit is rejected",
                    !denied.isOk() && denied.getText().contains("not yours"));

            bob.send(new TradeReplyRequest(offer1Id, true));
            ok = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("trade: accept goes through in bob's own turn", ok.isOk());
            bob.expect(GameStateBroadcast.TYPE);
            alice.expect(GameStateBroadcast.TYPE);
            bob.send(new TradeReplyRequest(offer2Id, false));
            ok = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("trade: reject goes through too", ok.isOk());
            GameStateBroadcast bobAfterTrades = (GameStateBroadcast) bob.expect(GameStateBroadcast.TYPE);
            GameStateBroadcast afterTrades = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            sut.civilization.Model.Classes.Game gameAfterTrades = decodeState(afterTrades);
            sut.civilization.Model.Classes.Game bobsView = decodeState(bobAfterTrades);
            // accepted: alice 20-5-2=13 wood +0, stone 7+3=10; rejected: 2 wood refunded -> 15
            check("trade: accept swapped the resources (alice stone 7 -> 10)",
                    findUser(gameAfterTrades, "alice").getNation().getStone().getBalance() == 10);
            check("trade: reject refunded the locked wood (alice wood back to 15)",
                    findUser(gameAfterTrades, "alice").getNation().getWood().getBalance() == 15);
            // each player's balances read from THEIR OWN packet - the opponent's copy is zeroed
            check("trade: bob paid 3 stone and got 5 wood (his own packet)",
                    findUser(bobsView, "bob").getNation().getStone().getBalance() == 4
                            && findUser(bobsView, "bob").getNation().getWood().getBalance() == 25);
            check("fog: the opponent's economy never travels (bob's balances are zero in alice's packet)",
                    findUser(gameAfterTrades, "bob").getNation().getWood().getBalance() == 0
                            && findUser(gameAfterTrades, "bob").getNation().getStone().getBalance() == 0);

            bob.send(new EndTurnRequest());
            bob.expect(ActionResultResponse.TYPE);
            alice.expect(GameStateBroadcast.TYPE); // world stepped to turn 2, alice again
            bob.expect(GameStateBroadcast.TYPE);

            // ── flat structure damage over the wire (P3-33) ──
            int thMax = bobUser.getNation().getTownHall().getMaxHp();
            alice.send(new AttackRequest(sword2Hex.x, sword2Hex.y, 11, 11));
            ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("pvp: attack on the enemy town hall goes through", ok.isOk());
            alice.expect(GameStateBroadcast.TYPE);
            BattleReportMessage thReport = (BattleReportMessage) alice.expect(BattleReportMessage.TYPE);
            check("pvp: buildings take no dice", thReport.getAttackerDice().length == 0);
            check("pvp: town hall took exactly the flat 10 damage",
                    bobUser.getNation().getTownHall().getHp() == thMax - 10);
            bob.expect(GameStateBroadcast.TYPE);

            // ── the kill: last town hall falls -> elimination + win (P3-01) ──
            bobUser.getNation().getTownHall().setHp(5); // rig: skip 20 more turns of chipping
            alice.send(new AttackRequest(sword2Hex.x, sword2Hex.y, 11, 11));
            ok = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("win: the killing blow goes through", ok.isOk());
            GameStateBroadcast endState = (GameStateBroadcast) alice.expect(GameStateBroadcast.TYPE);
            boolean eliminatedAnnounced = false, winAnnounced = false;
            for (String n : endState.getNotifications()) {
                if (n.contains("ELIMINATED")) eliminatedAnnounced = true;
                if (n.contains("WINS")) winAnnounced = true;
            }
            check("win: elimination is announced to everyone", eliminatedAnnounced);
            check("win: the winner is announced to everyone", winAnnounced);
            sut.civilization.Model.Classes.Game endGame = decodeState(endState);
            check("win: snapshot carries the winner", "alice".equals(endGame.getWinner()));
            check("win: bob is marked eliminated", endGame.isEliminated("bob"));
            check("win: bob's empire is wiped off the server's map",
                    bobUser.getNation().getUnits().isEmpty() && bobUser.getNation().getTownHall() == null);
            check("pvp: two attacks drained the sword's AP", sut.civilization.Controller.GameControllers
                    .CombatController.checkCanAttackHexPvp(sword2, thHex) != null);
            bob.expect(GameStateBroadcast.TYPE);

            // after the win the server accepts nothing more
            alice.send(new EndTurnRequest());
            denied = (ActionResultResponse) alice.expect(ActionResultResponse.TYPE);
            check("win: the game is over for the winner too", !denied.isOk() && denied.getText().contains("over"));
            bob.send(new EndTurnRequest());
            denied = (ActionResultResponse) bob.expect(ActionResultResponse.TYPE);
            check("win: the game is over for the loser", !denied.isOk());
        } finally {
            try {
                alice.close();
            } catch (Exception ignored) {
            }
            try {
                bob.close();
            } catch (Exception ignored) {
            }
            server.stopServer();
        }
    }

    // ── UDP heartbeat ────────────────────────────────────────────────────────

    static void testUdpHeartbeat() throws Exception {
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(2000);
            byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
            udp.send(new DatagramPacket(payload, payload.length, InetAddress.getByName("127.0.0.1"), PORT));
            DatagramPacket reply = new DatagramPacket(new byte[64], 64);
            udp.receive(reply);
            String text = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
            check("udp: heartbeat echoes back", text.equals("ping"));
        }
    }
}

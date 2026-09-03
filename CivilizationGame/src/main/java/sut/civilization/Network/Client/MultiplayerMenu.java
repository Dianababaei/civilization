package sut.civilization.Network.Client;

import sut.civilization.Network.Messages.JoinLobbyRequest;
import sut.civilization.Network.Server.GameServer;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.IOException;

/**
 * Entry point from the main menu: host a game (start a server in this process
 * and join it) or join someone else's server by address.
 */
public class MultiplayerMenu {

    public static void open(Component parent) {
        String[] options = {"Host Game", "Join Game", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent,
                "Host a new game on this computer, or join a friend's game?",
                "Multiplayer", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice != 0 && choice != 1) return;

        String username = JOptionPane.showInputDialog(parent, "Choose a username:", "Player");
        if (username == null || username.trim().isEmpty()) return;
        username = username.trim();

        // P3-51: a new username registers this password on the server; joining
        // again with the same name requires the same password
        javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField();
        int answer = JOptionPane.showConfirmDialog(parent, passwordField,
                "Password (new username = registers it):", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        String password = new String(passwordField.getPassword());

        String host;
        int port;
        if (choice == 0) {
            host = "127.0.0.1";
            // a leftover server from an earlier match may still hold the default
            // port - its lobby is stale ("game already started"), so instead of
            // connecting to it we host on the next free port
            port = findFreePort(GameServer.DEFAULT_PORT);
            try {
                spawnServerProcess(port);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parent,
                        "Could not start the server on port " + port + ":\n" + e.getMessage(),
                        "Host failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (port != GameServer.DEFAULT_PORT) {
                JOptionPane.showMessageDialog(parent,
                        "Port " + GameServer.DEFAULT_PORT + " is occupied (probably a leftover server"
                                + " from an earlier match).\nHosting on port " + port + " instead - players"
                                + " must join with 127.0.0.1:" + port + " (also shown in the lobby title).",
                        "Different port", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            String address = JOptionPane.showInputDialog(parent,
                    "Server address (ip:port):", "127.0.0.1:" + GameServer.DEFAULT_PORT);
            if (address == null || address.trim().isEmpty()) return;
            String[] parts = address.trim().split(":");
            host = parts[0];
            try {
                port = parts.length > 1 ? Integer.parseInt(parts[1]) : GameServer.DEFAULT_PORT;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(parent, "\"" + parts[1] + "\" is not a port number.",
                        "Bad address", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        NetworkManager network = new NetworkManager();
        IOException lastFailure = null;
        // a freshly spawned server process needs a moment to open its port
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                network.connect(host, port);
                lastFailure = null;
                break;
            } catch (IOException e) {
                lastFailure = e;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (lastFailure != null) {
            JOptionPane.showMessageDialog(parent,
                    "Could not connect to " + host + ":" + port + "\n" + lastFailure.getMessage(),
                    "Connection failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LobbyWindow lobby = new LobbyWindow(network, username, host + ":" + port);
        lobby.setVisible(true);
        network.send(new JoinLobbyRequest(username, password));
    }

    /** First port from `preferred` upward that nothing is listening on. */
    private static int findFreePort(int preferred) {
        for (int candidate = preferred; candidate < preferred + 20; candidate++) {
            try (java.net.ServerSocket probe = new java.net.ServerSocket(candidate)) {
                return candidate;
            } catch (IOException busy) {
                // taken - try the next one
            }
        }
        return preferred;
    }

    // ── hosting = spawning the real server as its own process ────────────────
    // The server must be its own JVM: all game logic runs through the
    // Game.instance singleton, and the authoritative game (server) and this
    // player's render copy (client) can not share one static field.
    // See docs/phase3-architecture.md, ADR 1.

    private static Process serverProcess;

    private static void spawnServerProcess(int port) throws IOException {
        String javaBin = System.getProperty("java.home") + java.io.File.separator
                + "bin" + java.io.File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(javaBin, "-cp", serverClasspath(),
                "sut.civilization.Network.Server.GameServer", String.valueOf(port));
        builder.redirectErrorStream(true);
        builder.redirectOutput(new java.io.File("server-log.txt"));
        serverProcess = builder.start();
        // the child must never outlive the game that spawned it
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (serverProcess != null) serverProcess.destroy();
        }));
    }

    /**
     * Classpath for the child JVM: located from our own classes and the Gson jar,
     * because java.class.path lies when the game was launched through Maven/IDE
     * wrapper classloaders.
     */
    private static String serverClasspath() {
        String own = locationOf(GameServer.class);
        String gson = locationOf(com.google.gson.Gson.class);
        if (own == null) return System.getProperty("java.class.path");
        return gson == null ? own : own + java.io.File.pathSeparator + gson;
    }

    private static String locationOf(Class<?> clazz) {
        try {
            return new java.io.File(clazz.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}

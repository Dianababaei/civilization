package sut.civilization.Network.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The game server: single source of truth for everything.
 *
 * Protocol split (phase 3 doc asks us to justify it):
 * - TCP: lobby, chat and every state-changing request. These need guaranteed,
 *   ordered delivery - a lost "start game" or chat line is unacceptable.
 * - UDP: heartbeat/ping only. Losing one ping packet costs nothing (the next
 *   one comes a second later), so the cheap connectionless protocol fits.
 *   Nothing that affects game state ever rides on UDP.
 */
public class GameServer {
    public static final int DEFAULT_PORT = 5555;

    private final int port;
    private final Lobby lobby = new Lobby();
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private volatile boolean running = false;

    public GameServer(int port) {
        this.port = port;
    }

    /** Binds both sockets and spawns the accept + heartbeat threads. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        udpSocket = new DatagramSocket(port);
        running = true;

        Thread acceptThread = new Thread(this::acceptLoop, "server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        Thread heartbeatThread = new Thread(this::heartbeatLoop, "server-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        System.out.println("Server listening on port " + port + " (TCP + UDP heartbeat)");
    }

    /** Thread-per-connection: every accepted client gets its own handler thread. */
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                new ClientHandler(client, lobby).start();
            } catch (IOException e) {
                if (running) System.out.println("Accept failed: " + e.getMessage());
            }
        }
    }

    /** UDP echo: whatever a client sends comes straight back, so it can measure RTT. */
    private void heartbeatLoop() {
        byte[] buffer = new byte[64];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                udpSocket.send(new DatagramPacket(packet.getData(), packet.getLength(),
                        packet.getAddress(), packet.getPort()));
            } catch (IOException e) {
                if (running) System.out.println("Heartbeat failed: " + e.getMessage());
            }
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (udpSocket != null) udpSocket.close();
    }

    public Lobby getLobby() {
        return lobby;
    }

    public int getPort() {
        return port;
    }

    /** Standalone entry point: java ... GameServer [port] */
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GameServer server = new GameServer(port);
        server.start();
        Thread.currentThread().join(); // keep the process alive
    }
}

package sut.civilization.Network.Client;

import sut.civilization.Network.Messages.Message;
import sut.civilization.Network.Messages.MessageCodec;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The client's only door to the network (the UI never touches sockets).
 *
 * Two extra threads, exactly as the doc's client architecture asks:
 * - listener thread: blocks on the TCP socket, parses each pushed message and
 *   hands it to the observers ON THE EDT via SwingUtilities.invokeLater - the
 *   UI thread itself never waits on the network.
 * - ping thread: sends a tiny UDP packet every 2 seconds and measures the
 *   round-trip time (this is the "fast but allowed to be lost" traffic).
 */
public class NetworkManager {

    /** Observer interface - the UI registers itself and reacts to events. */
    public interface Listener {
        void onMessage(Message message);

        void onDisconnected();
    }

    private Socket socket;
    private PrintWriter out;
    private DatagramSocket udpSocket;
    private volatile boolean connected = false;
    private volatile int pingMs = -1;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** P3-51: the JWT received at login, attached to every outgoing message. */
    private volatile String authToken;

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;

        Thread listenerThread = new Thread(() -> listenLoop(socket), "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        Thread pingThread = new Thread(() -> pingLoop(host, port), "client-ping");
        pingThread.setDaemon(true);
        pingThread.start();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Fire-and-forget: the server answers asynchronously through the listener. */
    public void send(Message message) {
        if (!connected) return;
        // P3-51: every request after login carries the JWT for the server to verify
        if (authToken != null) message.setToken(authToken);
        synchronized (out) {
            out.println(MessageCodec.encode(message));
        }
    }

    private void listenLoop(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                Message message = MessageCodec.decode(line);
                SwingUtilities.invokeLater(() -> {
                    for (Listener l : listeners) l.onMessage(message);
                });
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // fall through to the disconnect notification
        }
        boolean wasConnected = connected;
        connected = false;
        if (wasConnected)
            SwingUtilities.invokeLater(() -> {
                for (Listener l : listeners) l.onDisconnected();
            });
    }

    private void pingLoop(String host, int port) {
        try (DatagramSocket udp = new DatagramSocket()) {
            udpSocket = udp;
            udp.setSoTimeout(1000);
            byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(host);
            while (connected) {
                try {
                    long before = System.currentTimeMillis();
                    udp.send(new DatagramPacket(payload, payload.length, address, port));
                    udp.receive(new DatagramPacket(new byte[64], 64));
                    pingMs = (int) (System.currentTimeMillis() - before);
                } catch (SocketTimeoutException e) {
                    pingMs = -1; // lost packet: fine, UDP carries nothing important
                }
                Thread.sleep(2000);
            }
        } catch (IOException | InterruptedException ignored) {
        }
    }

    public int getPingMs() {
        return pingMs;
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        if (udpSocket != null) udpSocket.close();
    }
}

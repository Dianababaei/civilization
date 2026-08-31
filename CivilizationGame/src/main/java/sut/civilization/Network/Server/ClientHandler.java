package sut.civilization.Network.Server;

import sut.civilization.Network.Messages.ChatSendRequest;
import sut.civilization.Network.Messages.ChooseMapRequest;
import sut.civilization.Network.Messages.ErrorResponse;
import sut.civilization.Network.Messages.JoinLobbyRequest;
import sut.civilization.Network.Messages.JoinResultResponse;
import sut.civilization.Network.Messages.Message;
import sut.civilization.Network.Messages.MessageCodec;
import sut.civilization.Network.Messages.SetReadyRequest;
import sut.civilization.Network.Messages.StartGameRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One thread per connected client (thread-per-connection): reads line-JSON
 * requests off the TCP socket, asks the Lobby to handle them, and owns the
 * only writer for this socket. When the stream dies for any reason the
 * finally-block removes the player and frees the socket, so a client crash
 * never brings down the server or the other clients.
 */
public class ClientHandler extends Thread {
    private final Socket socket;
    private final Lobby lobby;

    // entity fields required by the doc for everything the server keeps
    private final String playerId = UUID.randomUUID().toString();
    private final long joinedAt = System.currentTimeMillis();

    private String username; // null until a JoinLobbyRequest is accepted
    private boolean ready = false;
    private PrintWriter out;

    public ClientHandler(Socket socket, Lobby lobby) {
        this.socket = socket;
        this.lobby = lobby;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    handle(MessageCodec.decode(line));
                } catch (IllegalArgumentException e) {
                    send(new ErrorResponse("Bad message: " + e.getMessage()));
                }
            }
        } catch (IOException ignored) {
            // connection dropped - handled by the cleanup below
        } finally {
            if (username != null) lobby.leave(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handle(Message message) {
        if (username == null) {
            // the only thing an unnamed connection may do is join;
            // the lobby answers with a JoinResultResponse itself
            if (message instanceof JoinLobbyRequest) {
                lobby.join(this, ((JoinLobbyRequest) message).getUsername());
            } else {
                send(new ErrorResponse("Join the lobby first."));
            }
            return;
        }

        // chat works the same before and during the game (it never touches turns)
        if (message instanceof ChatSendRequest) {
            String chatProblem = lobby.chat(this, ((ChatSendRequest) message).getText());
            if (chatProblem != null) send(new ErrorResponse(chatProblem));
            return;
        }

        // once the match runs, everything else belongs to the game session
        GameSession session = lobby.getSession();
        if (session != null) {
            session.handle(this, message);
            return;
        }

        String reason;
        if (message instanceof SetReadyRequest) {
            lobby.setReady(this, ((SetReadyRequest) message).isReady());
            reason = null;
        } else if (message instanceof ChooseMapRequest) {
            reason = lobby.chooseMap(this, ((ChooseMapRequest) message).getMapName());
        } else if (message instanceof StartGameRequest) {
            reason = lobby.startGame(this);
        } else {
            reason = "The server does not accept \"" + message.getType() + "\" here.";
        }
        if (reason != null) send(new ErrorResponse(reason));
    }

    /** The lobby broadcasts from other players' threads too, hence synchronized. */
    public void send(Message message) {
        PrintWriter writer = out;
        if (writer == null) return;
        synchronized (writer) {
            writer.println(MessageCodec.encode(message));
        }
    }

    public String getPlayerId() {
        return playerId;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}

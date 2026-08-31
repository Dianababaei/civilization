package sut.civilization.Network.Client;

import sut.civilization.Network.Messages.ChatBroadcast;
import sut.civilization.Network.Messages.ChatSendRequest;
import sut.civilization.Network.Messages.ChooseMapRequest;
import sut.civilization.Network.Messages.ErrorResponse;
import sut.civilization.Network.Messages.GameStartingBroadcast;
import sut.civilization.Network.Messages.JoinResultResponse;
import sut.civilization.Network.Messages.LobbyPlayerInfo;
import sut.civilization.Network.Messages.LobbyStateBroadcast;
import sut.civilization.Network.Messages.Message;
import sut.civilization.Network.Messages.SetReadyRequest;
import sut.civilization.Network.Messages.StartGameRequest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The multiplayer waiting room. Pure View: it only reacts to messages pushed
 * by the server (via NetworkManager's observer callbacks, already on the EDT)
 * and turns button clicks into requests. It never decides anything itself.
 */
public class LobbyWindow extends JFrame implements NetworkManager.Listener {
    private final NetworkManager network;
    private final String myName;

    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final JComboBox<String> mapCombo = new JComboBox<>();
    private final JButton readyButton = new JButton("Ready");
    private final JButton startButton = new JButton("Start Game");
    private final JLabel pingLabel = new JLabel("ping: -");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatField = new JTextField();

    private boolean iAmHost = false;
    private boolean iAmReady = false;
    private boolean joined = false;
    private boolean updatingMapCombo = false;

    public LobbyWindow(NetworkManager network, String myName, String serverAddress) {
        // the address is in the title so the host can read the port to friends
        // (hosting picks a different port when the default one is occupied)
        super("Multiplayer Lobby - " + myName + "  [server: " + serverAddress + "]");
        this.network = network;
        this.myName = myName;
        buildUi();
        network.addListener(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                network.close();
            }
        });
    }

    private void buildUi() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 460);
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.setBackground(new Color(0x1e1e1e));
        setContentPane(root);

        // top bar: map choice + ready + start + ping
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        JLabel mapLabel = new JLabel("Map:");
        mapLabel.setForeground(Color.WHITE);
        top.add(mapLabel);
        top.add(mapCombo);
        top.add(readyButton);
        top.add(startButton);
        pingLabel.setForeground(new Color(0x9acd68));
        top.add(pingLabel);
        root.add(top, BorderLayout.NORTH);

        // left: live player list
        JList<String> playerList = new JList<>(playerModel);
        playerList.setBackground(new Color(0x2a2a2a));
        playerList.setForeground(new Color(0xf0c060));
        JScrollPane playerScroll = new JScrollPane(playerList);
        playerScroll.setPreferredSize(new Dimension(200, 0));
        playerScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Players",
                0, 0, null, Color.WHITE));
        root.add(playerScroll, BorderLayout.WEST);

        // center: chat
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setBackground(new Color(0x2a2a2a));
        chatArea.setForeground(Color.WHITE);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Chat",
                0, 0, null, Color.WHITE));
        root.add(chatScroll, BorderLayout.CENTER);

        JPanel chatBar = new JPanel(new BorderLayout(5, 0));
        chatBar.setOpaque(false);
        chatBar.add(chatField, BorderLayout.CENTER);
        JButton sendButton = new JButton("Send");
        chatBar.add(sendButton, BorderLayout.EAST);
        root.add(chatBar, BorderLayout.SOUTH);

        // actions: clicks only produce requests, the server answers by push
        readyButton.addActionListener(e -> {
            iAmReady = !iAmReady;
            network.send(new SetReadyRequest(iAmReady));
        });
        startButton.addActionListener(e -> network.send(new StartGameRequest()));
        mapCombo.addActionListener(e -> {
            if (updatingMapCombo || !iAmHost || mapCombo.getSelectedItem() == null) return;
            network.send(new ChooseMapRequest((String) mapCombo.getSelectedItem()));
        });
        Runnable sendChat = () -> {
            String text = chatField.getText().trim();
            if (!text.isEmpty()) {
                network.send(new ChatSendRequest(text));
                chatField.setText("");
            }
        };
        sendButton.addActionListener(e -> sendChat.run());
        chatField.addActionListener(e -> sendChat.run());

        startButton.setEnabled(false);
        startButton.setToolTipText("Only the host can start, and everyone must be Ready.");
        mapCombo.setEnabled(false);
        mapCombo.setToolTipText("Only the host chooses the map.");

        // refresh the UDP ping readout once a second
        new Timer(1000, e -> {
            int ping = network.getPingMs();
            pingLabel.setText(ping < 0 ? "ping: -" : "ping: " + ping + " ms");
        }).start();
    }

    // ── server pushes (already on the EDT) ───────────────────────────────────

    @Override
    public void onMessage(Message message) {
        if (message instanceof JoinResultResponse) {
            JoinResultResponse join = (JoinResultResponse) message;
            if (!join.isOk()) {
                JOptionPane.showMessageDialog(this, join.getReason(), "Can not join", JOptionPane.ERROR_MESSAGE);
                network.close();
                dispose();
            } else {
                joined = true;
            }
        } else if (message instanceof LobbyStateBroadcast) {
            showLobbyState((LobbyStateBroadcast) message);
        } else if (message instanceof ChatBroadcast) {
            ChatBroadcast chat = (ChatBroadcast) message;
            chatArea.append("[" + chat.getTime() + "] " + chat.getSender() + ": " + chat.getText() + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } else if (message instanceof ErrorResponse) {
            JOptionPane.showMessageDialog(this, ((ErrorResponse) message).getReason(),
                    "Server says no", JOptionPane.WARNING_MESSAGE);
        } else if (message instanceof GameStartingBroadcast) {
            // hand the connection over to the match and close the lobby - the first
            // GameStateBroadcast right behind this message opens the game screen
            MultiplayerSession.begin(network, myName, chatArea.getText());
            network.removeListener(this);
            dispose();
        }
    }

    private void showLobbyState(LobbyStateBroadcast state) {
        playerModel.clear();
        boolean everyoneReady = true;
        for (LobbyPlayerInfo p : state.getPlayers()) {
            String row = p.getName()
                    + (p.isHost() ? "  (Host)" : "")
                    + (p.isReady() ? "  [READY]" : "");
            if (p.getName().equals(myName)) {
                row += "  <- you";
                iAmHost = p.isHost();
                iAmReady = p.isReady();
            }
            if (!p.isReady()) everyoneReady = false;
            playerModel.addElement(row);
        }
        readyButton.setText(iAmReady ? "Not Ready" : "Ready");
        startButton.setEnabled(iAmHost && everyoneReady && state.getPlayers().size() >= 2);
        mapCombo.setEnabled(iAmHost);

        updatingMapCombo = true; // refreshing the combo must not echo a request
        mapCombo.removeAllItems();
        for (String map : state.getMaps()) mapCombo.addItem(map);
        mapCombo.setSelectedItem(state.getChosenMap());
        updatingMapCombo = false;
    }

    @Override
    public void onDisconnected() {
        if (!isDisplayable()) return; // window already closed by the user
        JOptionPane.showMessageDialog(this, "Connection to the server was lost.",
                "Disconnected", JOptionPane.ERROR_MESSAGE);
        dispose();
    }
}

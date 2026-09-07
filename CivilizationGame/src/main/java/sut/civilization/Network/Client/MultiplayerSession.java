package sut.civilization.Network.Client;

import sut.civilization.Controller.GameControllers.CityController;
import sut.civilization.Controller.GameControllers.GameController;
import sut.civilization.Controller.GameControllers.LandController;
import sut.civilization.Controller.GameControllers.TechnologyController;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.Game;
import sut.civilization.Model.Classes.Pair;
import sut.civilization.Model.Classes.User;
import sut.civilization.Network.Messages.ActionResultResponse;
import sut.civilization.Network.Messages.ChatBroadcast;
import sut.civilization.Network.Messages.ChatSendRequest;
import sut.civilization.Network.Messages.EndTurnRequest;
import sut.civilization.Network.Messages.ErrorResponse;
import sut.civilization.Network.Messages.GameStateBroadcast;
import sut.civilization.Network.Messages.Message;
import sut.civilization.Network.Messages.MoveUnitRequest;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client-side state of a running multiplayer match. Applies every
 * GameStateBroadcast the exact same way phase 2 load applies a save file:
 * swap Game.instance, rebuild the caches, rebuild the screen. The UI asks
 * this class whose turn it is and sends its requests through it - it is the
 * only bridge between the game screen and the network.
 */
public class MultiplayerSession implements NetworkManager.Listener {

    private static MultiplayerSession active = null;

    private final NetworkManager network;
    private final String myName;
    private String currentPlayer = "";
    private final List<String> chatLog = new ArrayList<>();
    private JTextArea chatArea; // non-null while the chat window is open

    private MultiplayerSession(NetworkManager network, String myName) {
        this.network = network;
        this.myName = myName;
    }

    /** Called by the lobby window the moment the server says the game starts.
     *  The lobby's chat text is carried over so the in-game chat window does
     *  not open empty after a conversation in the lobby. */
    public static void begin(NetworkManager network, String myName, String lobbyChatText) {
        active = new MultiplayerSession(network, myName);
        if (lobbyChatText != null)
            for (String line : lobbyChatText.split("\n"))
                if (!line.trim().isEmpty()) active.chatLog.add(line);
        network.addListener(active);
    }

    public static boolean isActive() {
        return active != null;
    }

    public static MultiplayerSession get() {
        return active;
    }

    public static boolean isMyTurn() {
        return active != null && active.myName.equals(active.currentPlayer);
    }

    public static String currentPlayerName() {
        return active == null ? "" : active.currentPlayer;
    }

    // ── requests (the game screen calls these instead of local controllers) ──

    /** My last move request, remembered so the next snapshot can animate it. */
    private Pair<Integer, Integer> pendingMoveFrom, pendingMoveTo;

    public void sendMove(Pair<Integer, Integer> from, int toX, int toY) {
        pendingMoveFrom = new Pair<>(from.x, from.y);
        pendingMoveTo = new Pair<>(toX, toY);
        network.send(new MoveUnitRequest(from.x, from.y, toX, toY));
    }

    public void sendEndTurn() {
        network.send(new EndTurnRequest());
    }

    public void sendChat(String text) {
        // pasted commands often carry invisible direction marks (U+200E/200F,
        // zero-width space, BOM) in front of the "/" - strip them from the
        // START only, the rest of the message may be legit Persian text
        if (text != null) text = text.replaceAll("^[\\u200b\\u200e\\u200f\\ufeff\\s]+", "");
        // P3-48: a leading "/" is a CHEAT command - it goes to the server as a
        // cheat request (never executed locally); everything else is chat
        if (text != null && text.startsWith("/")) {
            network.send(new sut.civilization.Network.Messages.CheatCommandRequest(text));
        } else {
            network.send(new ChatSendRequest(text));
        }
    }

    public String getMyName() {
        return myName;
    }

    public void sendAttack(Pair<Integer, Integer> from, int toX, int toY) {
        network.send(new sut.civilization.Network.Messages.AttackRequest(from.x, from.y, toX, toY));
    }

    public void sendDeclareWar(String target) {
        network.send(new sut.civilization.Network.Messages.DeclareWarRequest(target));
    }

    public void sendOfferAlliance(String target) {
        network.send(new sut.civilization.Network.Messages.OfferAllianceRequest(target));
    }

    public void sendAllianceReply(String from, boolean accept) {
        network.send(new sut.civilization.Network.Messages.AllianceReplyRequest(from, accept));
    }

    public void sendBreakAlliance(String target) {
        network.send(new sut.civilization.Network.Messages.BreakAllianceRequest(target));
    }

    public void sendTradeOffer(String to, int[] give, int[] want) {
        network.send(new sut.civilization.Network.Messages.TradeOfferRequest(to,
                give[0], give[1], give[2], give[3], want[0], want[1], want[2], want[3]));
    }

    public void sendTradeReply(String offerId, boolean accept) {
        network.send(new sut.civilization.Network.Messages.TradeReplyRequest(offerId, accept));
    }

    public void sendTrainUnit(String unitName) {
        network.send(new sut.civilization.Network.Messages.TrainUnitRequest(unitName));
    }

    public void sendDebugAction(String action) {
        network.send(new sut.civilization.Network.Messages.DebugActionRequest(action));
    }

    public void sendUpgradeTownHall() {
        network.send(new sut.civilization.Network.Messages.UpgradeTownHallRequest());
    }

    public void sendResearch(String techName) {
        network.send(new sut.civilization.Network.Messages.ResearchRequest(techName));
    }

    public void sendBuildImprovement(Pair<Integer, Integer> at, String improvementName) {
        network.send(new sut.civilization.Network.Messages.BuildImprovementRequest(at.x, at.y, improvementName));
    }

    public void sendStation(Pair<Integer, Integer> at, boolean station) {
        network.send(new sut.civilization.Network.Messages.StationRequest(at.x, at.y, station));
    }

    // ── server pushes (NetworkManager already put us on the EDT) ─────────────

    @Override
    public void onMessage(Message message) {
        if (message instanceof GameStateBroadcast) {
            applySnapshot((GameStateBroadcast) message);
        } else if (message instanceof ActionResultResponse) {
            ActionResultResponse result = (ActionResultResponse) message;
            // rejections must reach the player word for word; accepted moves are
            // visible on the map anyway, a popup for them would just be noise
            if (!result.isOk()) {
                pendingMoveFrom = pendingMoveTo = null; // nothing to animate
                showInfo(result.getText());
            }
        } else if (message instanceof ChatBroadcast) {
            ChatBroadcast chat = (ChatBroadcast) message;
            chatLog.add("[" + chat.getTime() + "] " + chat.getSender() + ": " + chat.getText());
            if (chatArea != null) {
                chatArea.append(chatLog.get(chatLog.size() - 1) + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        } else if (message instanceof ErrorResponse) {
            showInfo(((ErrorResponse) message).getReason());
        } else if (message instanceof sut.civilization.Network.Messages.BattleReportMessage) {
            // a battle involving us was resolved on the server - show the same
            // dice dialog single-player uses (war report, P3-34)
            sut.civilization.View.Graphical.GamePlayController screen =
                    sut.civilization.View.Graphical.GamePlayController.getInstance();
            if (screen != null)
                screen.showNetworkBattle((sut.civilization.Network.Messages.BattleReportMessage) message);
        }
    }

    @Override
    public void onDisconnected() {
        end("Connection to the server was lost.");
    }

    private void applySnapshot(GameStateBroadcast broadcast) {
        Game loaded;
        try {
            byte[] bytes = Base64.getDecoder().decode(broadcast.getStateBase64());
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                loaded = (Game) in.readObject();
            }
        } catch (Exception e) {
            showInfo("Bad game state from the server: " + e.getMessage());
            return;
        }

        currentPlayer = broadcast.getCurrentPlayer();

        // same steps as SaveController.loadGame EXCEPT the distance matrix: all
        // multiplayer pathfinding runs on the SERVER, the client never reads it -
        // and recomputing 225x225 all-pairs paths here froze the UI thread for
        // seconds on EVERY broadcast (open dialogs stopped painting and looked
        // blank). The arrays stay allocated so a stray read sees zeros, not null
        loaded.adoptWindowFrom(Game.instance);
        Game.instance = loaded;
        loaded.dist = new int[225][225];
        loaded.path = new String[225][225];

        User me = null;
        for (User user : loaded.getPlayersInGame())
            if (user.getUsername().equals(myName)) me = user;
        if (me == null) {
            end("The server no longer knows this player.");
            return;
        }
        loaded.setLoggedInUser(me);
        GameController.setCurrentTurnUser(me);
        GameController.setSelectedCivilizedUnit(null);
        TechnologyController.updateNextAvailableTechnologies();
        CityController.updateAffordableLands();

        // once the game screen exists, apply updates IN PLACE (fast, camera
        // untouched, own moves animated); the full scene build runs only for
        // the very first snapshot that opens the match
        sut.civilization.View.Graphical.GamePlayController screen =
                sut.civilization.View.Graphical.GamePlayController.getInstance();
        if (screen != null && screen.root != null && screen.root.isShowing()) {
            screen.refreshFromSnapshot(pendingMoveFrom, pendingMoveTo);
        } else {
            Game.instance.changeScene(Menus.GAME_MENU);
        }
        pendingMoveFrom = pendingMoveTo = null;

        // end-of-round world report (seasons, disasters, starvation, ...)
        if (broadcast.getNotifications() != null && !broadcast.getNotifications().isEmpty()) {
            StringBuilder report = new StringBuilder("<html><b>Turn " + broadcast.getTurn() + "</b>");
            for (String n : broadcast.getNotifications()) report.append("<br>• ").append(n);
            showInfo(report.append("</html>").toString());
        }
    }

    /** Leaves the match: closes the connection and returns to the main menu. */
    public void end(String reason) {
        active = null;
        network.close();
        if (Game.instance.getMainWindow() != null) {
            showInfo(reason);
            Game.instance.changeScene(Menus.MAIN_MENU);
        }
    }

    // ── in-game chat window ──────────────────────────────────────────────────

    private JDialog chatDialog; // reused: clicking Chat twice must not stack windows

    public void openChat(Component parent) {
        if (chatDialog != null && chatDialog.isDisplayable()) {
            chatDialog.setVisible(true);
            chatDialog.toFront();
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Game Chat");
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(360, 320);
        dialog.setLocationRelativeTo(parent);

        chatArea = new JTextArea(String.join("\n", chatLog) + (chatLog.isEmpty() ? "" : "\n"));
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setBackground(new Color(0x2a2a2a));
        chatArea.setForeground(Color.WHITE);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());

        JTextField field = new JTextField();
        JButton sendButton = new JButton("Send");
        Runnable send = () -> {
            String text = field.getText().trim();
            if (!text.isEmpty()) sendChat(text);
            field.setText("");
            field.requestFocusInWindow();
        };
        field.addActionListener(e -> send.run());
        sendButton.addActionListener(e -> send.run());

        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.add(field, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        dialog.setLayout(new BorderLayout(5, 5));
        dialog.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                chatArea = null;
                chatDialog = null;
            }
        });
        chatDialog = dialog;
        dialog.setVisible(true);
        field.requestFocusInWindow();
    }

    private void showInfo(String text) {
        JOptionPane.showMessageDialog(Game.instance.getMainWindow(), text);
    }
}

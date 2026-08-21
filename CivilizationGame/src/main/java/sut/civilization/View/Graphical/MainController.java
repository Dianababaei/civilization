package sut.civilization.View.Graphical;

import sut.civilization.Controller.GameControllers.CityController;
import sut.civilization.Controller.GameControllers.GameController;
import sut.civilization.Controller.GameControllers.LandController;
import sut.civilization.Controller.GameControllers.SaveController;
import sut.civilization.Controller.GameControllers.TechnologyController;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.Game;
import sut.civilization.Model.Classes.Nation;
import sut.civilization.Model.Classes.User;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;

public class MainController extends ViewController {

    private JPanel settingsPanel;
    private JSlider musicSlider;

    // Shared Clip - kept alive for the lifetime of the main menu screen
    private static Clip musicClip;
    private static FloatControl volumeControl;

    public static JPanel build() {
        MainController controller = new MainController();
        return controller.createPanel();
    }

    private JPanel createPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x1e1e1e));

        JLabel title = new JLabel("Civilization V", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 48));
        title.setForeground(new Color(0xf0c060));
        title.setBorder(BorderFactory.createEmptyBorder(60, 0, 40, 0));
        root.add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JButton startButton = styledButton("Start");
        JButton loadButton = styledButton("Load Game");
        JButton settingsButton = styledButton("Settings");
        JButton exitButton = styledButton("Exit");

        for (JButton b : new JButton[]{startButton, loadButton, settingsButton, exitButton}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(220, 45));
            centerPanel.add(Box.createVerticalStrut(15));
            centerPanel.add(b);
        }

        settingsPanel = new JPanel();
        settingsPanel.setOpaque(false);
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setVisible(false);

        JLabel volumeLabel = new JLabel("Music Volume");
        volumeLabel.setForeground(Color.WHITE);
        volumeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        musicSlider = new JSlider(0, 100, 70);
        musicSlider.setMaximumSize(new Dimension(220, 40));
        musicSlider.setAlignmentX(Component.CENTER_ALIGNMENT);
        musicSlider.addChangeListener(e -> onVolumeChanged());

        settingsPanel.add(Box.createVerticalStrut(15));
        settingsPanel.add(volumeLabel);
        settingsPanel.add(musicSlider);

        centerPanel.add(settingsPanel);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(centerPanel);
        root.add(wrapper, BorderLayout.CENTER);

        startButton.addActionListener(e -> onStart());
        loadButton.addActionListener(e -> onLoad(root));
        settingsButton.addActionListener(e -> onSettings());
        exitButton.addActionListener(e -> onExit(root));

        tryStartMusic();

        return root;
    }

    private JButton styledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setBackground(new Color(0x3a2f1f));
        button.setForeground(new Color(0xf0c060));
        button.setFocusPainted(false);
        return button;
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    private void onStart() {
        try {
            // Doc: the player sets the initial Unit Cap themselves, "logically" - not a
            // fixed constant. Starting units alone number 5 (1 Explorer + 2 Builders +
            // 2 Workers), so the minimum accepted here is 5.
            int chosenUnitCap = promptForUnitCap();
            if (chosenUnitCap < 0) return; // player cancelled

            User user = Game.instance.getLoggedInUser();
            if (user == null) {
                user = new User("player", "", "Player");
                Game.instance.setLoggedInUser(user);
            }
            Game.instance.getPlayersInGame().clear();
            Nation nation = new Nation();
            nation.setBaseUnitCap(chosenUnitCap);
            user.setNation(nation);
            Game.instance.getPlayersInGame().add(user);
            GameController.setCurrentTurnUser(user);

            Game.instance.setMap(LandController.mapInitializer());
            LandController.initializeDistances();
            LandController.updateDistances();
            TechnologyController.updateNextAvailableTechnologies();
            CityController.updateAffordableLands();

            // Doc: the background soundtrack must be persistent - keep it playing into
            // gameplay rather than stopping it here (previously stopped and never resumed).
            Game.instance.changeScene(Menus.GAME_MENU);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to start: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /** Prompts for the initial Unit Cap. Returns the chosen value, or -1 if the player cancelled. */
    private int promptForUnitCap() {
        final int MIN_CAP = 5; // starting units: 1 Explorer + 2 Builders + 2 Workers
        while (true) {
            Object result = JOptionPane.showInputDialog(null,
                    "Choose your initial Unit Cap (minimum " + MIN_CAP + "):",
                    "Initial Unit Cap", JOptionPane.QUESTION_MESSAGE, null, null, MIN_CAP);
            if (result == null) return -1;
            String input = result.toString();
            try {
                int value = Integer.parseInt(input.trim());
                if (value >= MIN_CAP) return value;
                JOptionPane.showMessageDialog(null, "Unit Cap must be at least " + MIN_CAP + ".",
                        "Invalid value", JOptionPane.WARNING_MESSAGE);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Please enter a whole number.",
                        "Invalid value", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    // phase 2 save/load: pick a slot (3 manual + autosave), load it, jump into the game
    private void onLoad(Component parent) {
        String[] slots = {SaveController.SLOTS[0], SaveController.SLOTS[1], SaveController.SLOTS[2], SaveController.AUTOSAVE_SLOT};
        String[] options = new String[slots.length];
        for (int i = 0; i < slots.length; i++) options[i] = SaveController.describeSlot(slots[i]);
        int pick = JOptionPane.showOptionDialog(parent, "Choose a slot to load:",
                "Load Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (pick < 0) return;

        // damaged slot: mark it and offer deletion instead of trying to load it
        if (SaveController.isDamaged(slots[pick])) {
            int del = JOptionPane.showConfirmDialog(parent,
                    "The file in " + slots[pick] + " is damaged and cannot be loaded.\nDelete it?",
                    "Damaged save", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (del == JOptionPane.YES_OPTION)
                JOptionPane.showMessageDialog(parent, SaveController.deleteSlot(slots[pick]));
            return;
        }

        String message = SaveController.loadGame(slots[pick]);
        if (message.startsWith("Loaded")) {
            Game.instance.changeScene(Menus.GAME_MENU);
        }
        JOptionPane.showMessageDialog(parent, message);
    }

    private void onSettings() {
        boolean nowVisible = !settingsPanel.isVisible();
        settingsPanel.setVisible(nowVisible);
        settingsPanel.revalidate();
        settingsPanel.repaint();
    }

    private void onVolumeChanged() {
        if (volumeControl != null) {
            float value = musicSlider.getValue() / 100f;
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float gain = min + (max - min) * value;
            volumeControl.setValue(gain);
        }
    }

    private void onExit(Component parent) {
        try {
            int result = JOptionPane.showConfirmDialog(parent, "Are you sure you want to exit?",
                    "Exit", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                stopMusic();
                System.exit(0);
            }
        } catch (Exception e) {
            System.exit(0);
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    private void tryStartMusic() {
        if (musicClip != null) {
            if (volumeControl != null) {
                float value = musicSlider.getValue() / 100f;
                float min = volumeControl.getMinimum();
                float max = volumeControl.getMaximum();
                volumeControl.setValue(min + (max - min) * value);
            }
            return;
        }
        try {
            URL musicUrl = getClass().getResource("/sut/civilization/Music/Theme.wav");
            if (musicUrl == null) return;
            try (InputStream in = new BufferedInputStream(musicUrl.openStream())) {
                musicClip = AudioSystem.getClip();
                musicClip.open(AudioSystem.getAudioInputStream(in));
                if (musicClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    volumeControl = (FloatControl) musicClip.getControl(FloatControl.Type.MASTER_GAIN);
                    onVolumeChanged();
                }
                musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        } catch (Exception ignored) {
            // Audio file missing or unsupported format - skip silently.
            musicClip = null;
        }
    }

    public static void stopMusic() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
        }
    }
}

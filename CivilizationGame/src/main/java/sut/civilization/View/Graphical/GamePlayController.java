package sut.civilization.View.Graphical;

import sut.civilization.Civilization;
import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Enums.CommonIcons;
import sut.civilization.Model.Classes.TownHall;
import sut.civilization.Model.Classes.Nation;
import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.TechnologyType;
import sut.civilization.Enums.Consts;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;
import sut.civilization.Network.Client.MultiplayerSession;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

public class GamePlayController extends ViewController {

    private final static LandGraphical[][] graphicalMap = new LandGraphical[Consts.MAP_SIZE.amount.x][Consts.MAP_SIZE.amount.y];
    private JPanel mapPane;
    private Dimension mapBaseSize;
    private JScrollPane mapScrollPane;
    private static final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    private int zoomIndex = 2; // starts at 1.0

    public JPanel root;
    private JLayeredPane layeredRoot;
    private JPanel overlayLayer;
    private JPanel hudPanel;
    private WeatherOverlay weatherOverlay;

    public JLabel foodInfo;
    public JLabel woodInfo;
    public JLabel stoneInfo;
    public JLabel ironInfo;
    public JLabel unitCapInfo;
    public JLabel happinessInfo;
    public JLabel starvationLabel;
    public JLabel buildingWarningLabel;
    public JLabel turn;
    public JLabel yearLabel;
    public JLabel seasonLabel;

    public JLabel productionUnitName;
    public JLabel productionTurnsLeft;
    public JProgressBar productionProgress;

    public JLabel researchStatusLabel;

    private static GamePlayController gamePlayController;

    public static GamePlayController getInstance() {
        return gamePlayController;
    }

    public static JPanel build() {
        GamePlayController controller = new GamePlayController();
        return controller.createPanel();
    }

    private JPanel createPanel() {
        // phase 3: multiplayer rebuilds this screen on every server snapshot. The old
        // screen's floating unit popup must die with it, and the CAMERA (zoom +
        // scroll position) must carry over - otherwise every server update yanks
        // the player back to a default view of the map middle, which between two
        // corner spawns is nothing but fog
        int keepZoom = -1;
        java.awt.Point keepView = null;
        if (gamePlayController != null) {
            gamePlayController.hideUnitPopup();
            if (MultiplayerSession.isActive() && gamePlayController.mapScrollPane != null) {
                keepZoom = gamePlayController.zoomIndex;
                keepView = gamePlayController.mapScrollPane.getViewport().getViewPosition();
            }
        }
        gamePlayController = this;
        if (keepZoom >= 0) zoomIndex = keepZoom;
        final java.awt.Point restoredView = keepView;

        root = new JPanel(null);
        root.setBackground(new Color(0x1e1e1e));
        layeredRoot = new JLayeredPane();
        root.setLayout(new BorderLayout());
        root.add(layeredRoot, BorderLayout.CENTER);
        layeredRoot.setLayout(null);

        mapPane = new JPanel(null);
        mapPane.setOpaque(true);
        mapPane.setBackground(new Color(0x0d0d0d));

        LandController.updateLandVisibility();
        int maxX = 0, maxY = 0;
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                graphicalMap[i][j] = new LandGraphical(new Pair<>(i, j), mapPane);
                maxX = Math.max(maxX, graphicalMap[i][j].getX() + graphicalMap[i][j].getWidth());
                maxY = Math.max(maxY, graphicalMap[i][j].getY() + graphicalMap[i][j].getHeight());
            }
        }
        mapBaseSize = new Dimension(maxX + 100, maxY + 100);
        mapPane.setPreferredSize(mapBaseSize);

        // the whole map often FITS the window (fullscreen at zoom 1.0). A plain
        // JScrollPane pins a smaller-than-viewport view to the top-left corner,
        // which looks broken for corner spawns: base glued to the screen corner,
        // black band on the right. This holder centers the map instead whenever
        // there is spare room, and scrolls normally when there is not
        JPanel mapHolder = new JPanel(new GridBagLayout());
        mapHolder.setBackground(new Color(0x0d0d0d));
        mapHolder.add(mapPane);
        mapScrollPane = new JScrollPane(mapHolder);
        // stretch the holder to fill the viewport whenever it is larger than the
        // map, so the GridBagLayout above actually gets room to center in
        mapScrollPane.getViewport().addChangeListener(e -> {
            Dimension view = mapScrollPane.getViewport().getExtentSize();
            Dimension mapSize = mapPane.getPreferredSize();
            Dimension wanted = new Dimension(
                    Math.max(view.width, mapSize.width), Math.max(view.height, mapSize.height));
            if (!wanted.equals(mapHolder.getPreferredSize())) {
                mapHolder.setPreferredSize(wanted);
                mapHolder.revalidate();
            }
        });
        mapScrollPane.getVerticalScrollBar().setUnitIncrement(30);
        mapScrollPane.getHorizontalScrollBar().setUnitIncrement(30);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        // the HUD bar is an overlay painted ON TOP of the map area - start the map
        // viewport below it, otherwise the top row of tiles hides under the HUD
        // whenever the view is scrolled all the way up (corner spawns always are)
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(50, 0, 0, 0));
        centerWrapper.setBackground(new Color(0x1e1e1e));
        centerWrapper.add(mapScrollPane, BorderLayout.CENTER);
        centerWrapper.setBounds(0, 0, 1366, 768);
        layeredRoot.add(centerWrapper, JLayeredPane.DEFAULT_LAYER);

        overlayLayer = new JPanel(null);
        overlayLayer.setOpaque(false);
        overlayLayer.setBounds(0, 0, 1366, 768);
        layeredRoot.add(overlayLayer, JLayeredPane.PALETTE_LAYER);

        // phase 2: snow/rain particle layer - above the map but below the HUD/popups.
        // clicks pass straight through it (see WeatherOverlay.contains)
        weatherOverlay = new WeatherOverlay();
        weatherOverlay.setBounds(0, 0, 1366, 768);
        layeredRoot.add(weatherOverlay, Integer.valueOf(50));

        buildHud();
        buildBottomPanels();
        buildZoomControls();

        // a restored (or non-default) zoom level must actually rescale the tiles,
        // which were all constructed at scale 1.0 above
        if (zoomIndex != 2) applyZoom();

        // the default 1.5s hover delay makes tile tooltips ("Unexplored", terrain
        // info, disabled-button reasons) feel like they don't exist
        ToolTipManager.sharedInstance().setInitialDelay(300);

        root.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layeredRoot.setBounds(0, 0, root.getWidth(), root.getHeight());
                centerWrapper.setBounds(0, 0, root.getWidth(), root.getHeight());
                overlayLayer.setBounds(0, 0, root.getWidth(), root.getHeight());
                weatherOverlay.setBounds(0, 0, root.getWidth(), root.getHeight());
                // keep the HUD stretched across the full window width
                hudPanel.setBounds(0, 0, root.getWidth(), 46);
                layeredRoot.revalidate();
                layeredRoot.repaint();
            }
        });

        updateHud();

        // Position the initial view. The scrollbars' real extents aren't known until
        // the viewport has been laid out, so defer past one extra paint cycle rather
        // than a single invokeLater (which can still race layout).
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            mapScrollPane.revalidate();
            if (restoredView != null) {
                // multiplayer rebuild: exactly where the player was looking before
                mapScrollPane.getViewport().setViewPosition(restoredView);
                return;
            }
            JScrollBar hbar = mapScrollPane.getHorizontalScrollBar();
            JScrollBar vbar = mapScrollPane.getVerticalScrollBar();
            TownHall myTownHall = MultiplayerSession.isActive()
                    ? Game.instance.getLoggedInUser().getNation().getTownHall() : null;
            if (myTownHall != null) {
                // multiplayer first build: open on YOUR corner, not the fogged middle
                double zoom = ZOOM_LEVELS[zoomIndex];
                Pair<Double, Double> c = graphicalMap[myTownHall.getMainLand().getI()]
                        [myTownHall.getMainLand().getJ()].getCenterCoordinate();
                hbar.setValue(Math.max(0, (int) (c.x * zoom - mapScrollPane.getViewport().getWidth() / 2.0)));
                vbar.setValue(Math.max(0, (int) (c.y * zoom - mapScrollPane.getViewport().getHeight() / 2.0)));
            } else {
                // single player spawns mid-map
                hbar.setValue((int) (hbar.getMaximum() * 0.45));
                vbar.setValue((int) (vbar.getMaximum() * 0.45));
            }
        }));

        // Ctrl+Scroll steps through ZOOM_LEVELS; plain scroll falls through to the scroll pane's
        // own default handling since we only consume the event in the Ctrl-held branch.
        mapScrollPane.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) zoomIn(); else zoomOut();
                e.consume();
            }
        });

        return root;
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void buildHud() {
        // two-part bar: info labels flow on the left, buttons pinned on the right.
        // the old single FlowLayout wrapped once the season/starving labels got long,
        // which shoved the Debug/Menu buttons onto a clipped second row
        hudPanel = new JPanel(new BorderLayout());
        hudPanel.setBackground(new Color(0x22, 0x22, 0x22, 220));
        hudPanel.setBounds(0, 0, 1366, 46);

        JPanel hud = new JPanel();
        hud.setOpaque(false);
        hud.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 5));

        foodInfo = hudLabel();
        woodInfo = hudLabel();
        stoneInfo = hudLabel();
        ironInfo = hudLabel();
        unitCapInfo = hudLabel();
        happinessInfo = hudLabel();
        starvationLabel = hudLabel();
        starvationLabel.setText("STARVING!");
        starvationLabel.setForeground(Color.RED);
        starvationLabel.setVisible(false);
        buildingWarningLabel = hudLabel();
        buildingWarningLabel.setForeground(Color.ORANGE);
        buildingWarningLabel.setVisible(false);
        turn = hudLabel();
        yearLabel = hudLabel();
        seasonLabel = hudLabel();

        hud.add(labeled("Food:", foodInfo));
        hud.add(labeled("Wood:", woodInfo));
        hud.add(labeled("Stone:", stoneInfo));
        hud.add(labeled("Iron:", ironInfo));
        hud.add(labeled("Units:", unitCapInfo));
        hud.add(labeled("Happiness:", happinessInfo));
        hud.add(turn);
        hud.add(yearLabel);
        hud.add(seasonLabel);
        hud.add(starvationLabel);
        hud.add(buildingWarningLabel);

        // phase 3: whose turn it is must be visible at any moment (the whole screen
        // is rebuilt on every server snapshot, so this label is always fresh)
        if (MultiplayerSession.isActive()) {
            JLabel mpTurnLabel = hudLabel();
            if (MultiplayerSession.isMyTurn()) {
                mpTurnLabel.setText("YOUR TURN");
                mpTurnLabel.setForeground(new Color(0x9acd68));
            } else {
                mpTurnLabel.setText("Waiting for " + MultiplayerSession.currentPlayerName() + "...");
                mpTurnLabel.setForeground(new Color(0xe0685e));
            }
            hud.add(mpTurnLabel);

            // P3-41: the relation to every other player, always visible and color
            // coded (red = enemy, green = ally, gray = neutral) - never a guess
            String myName = MultiplayerSession.get().getMyName();
            for (User user : Game.instance.getPlayersInGame()) {
                if (user.getUsername().equals(myName)) continue;
                sut.civilization.Model.ModulEnums.Relation relation =
                        Game.instance.relationBetween(myName, user.getUsername());
                JLabel chip = hudLabel();
                chip.setText(user.getUsername() + ": "
                        + (Game.instance.isEliminated(user.getUsername()) ? "Eliminated" : relation.label));
                chip.setForeground(relationColor(relation));
                chip.setToolTipText("Your diplomatic state with " + user.getUsername());
                hud.add(chip);
            }
        }

        JButton townHallBtn = hudButton("Town Hall");
        JButton tradeBtn = hudButton("Trade");
        JButton unitsBtn = hudButton("Units");
        JButton statsBtn = hudButton("Stats");
        JButton debugBtn = hudButton("Debug");
        JButton menuBtn = hudButton("Menu");

        // phase 3: in multiplayer the Town Hall button opens the training dialog
        // (spec flow: armies are trained from resources, requests go to the server)
        townHallBtn.addActionListener(e -> {
            if (MultiplayerSession.isActive()) showMultiplayerTownHallDialog();
            else showTownHallPanel();
        });
        // phase 3: in multiplayer the Trade button opens the PLAYER trade dialog
        tradeBtn.addActionListener(e -> {
            if (MultiplayerSession.isActive()) showMultiplayerTradeDialog();
            else showTradePanel();
        });
        unitsBtn.addActionListener(e -> showUnits());
        statsBtn.addActionListener(e -> showDemographics());
        debugBtn.addActionListener(e -> { if (!blockInMultiplayer()) showDebugPanel(); });
        menuBtn.addActionListener(e -> showMenu());

        JPanel hudButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        hudButtons.setOpaque(false);
        hudButtons.add(townHallBtn);
        hudButtons.add(tradeBtn);
        hudButtons.add(unitsBtn);
        hudButtons.add(statsBtn);
        // phase 3: no local debug in a server-authoritative match - the server would
        // ignore local edits anyway, and cheats belong to the (skipped) bonus row
        if (!MultiplayerSession.isActive()) hudButtons.add(debugBtn);
        if (MultiplayerSession.isActive()) {
            JButton diplomacyBtn = hudButton("Diplomacy");
            diplomacyBtn.addActionListener(e -> showDiplomacyDialog());
            hudButtons.add(diplomacyBtn);
            JButton chatBtn = hudButton("Chat");
            chatBtn.addActionListener(e -> MultiplayerSession.get().openChat(root));
            hudButtons.add(chatBtn);
            // demo/cheat shortcuts for evaluation (P3-58): host-only, run by the
            // SERVER and announced to everyone - unlike the local phase-2 debug
            JButton demoBtn = hudButton("Demo");
            demoBtn.addActionListener(e -> showMultiplayerDemoDialog());
            hudButtons.add(demoBtn);
        }
        hudButtons.add(menuBtn);

        hudPanel.add(hud, BorderLayout.CENTER);
        hudPanel.add(hudButtons, BorderLayout.EAST);
        overlayLayer.add(hudPanel);
    }

    private JLabel hudLabel() {
        JLabel l = new JLabel();
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        return l;
    }

    private JPanel labeled(String prefix, JLabel valueLabel) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel pre = new JLabel(prefix);
        pre.setForeground(new Color(0xaaaaaa));
        pre.setFont(new Font("SansSerif", Font.PLAIN, 12));
        p.add(pre);
        p.add(valueLabel);
        return p;
    }

    /** True (and explains itself) when a single-player-only panel is opened mid-multiplayer. */
    private boolean blockInMultiplayer() {
        if (!MultiplayerSession.isActive()) return false;
        showPopUp(SwingUtilities.getWindowAncestor(root), "Not available in multiplayer yet.");
        return true;
    }

    private JButton hudButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBackground(new Color(0x3a2f1f));
        b.setForeground(new Color(0xf0c060));
        b.setFocusPainted(false);
        return b;
    }

    // ── Bottom panels ─────────────────────────────────────────────────────────

    private void buildBottomPanels() {
        JPanel productionPanel = new JPanel();
        productionPanel.setBackground(new Color(0x22, 0x22, 0x22, 220));
        productionPanel.setLayout(new BoxLayout(productionPanel, BoxLayout.Y_AXIS));
        productionPanel.setBounds(0, 700, 300, 68);

        productionUnitName = new JLabel("-");
        productionUnitName.setForeground(Color.WHITE);
        productionTurnsLeft = new JLabel("Queue empty");
        productionTurnsLeft.setForeground(new Color(0xaaaaaa));
        productionProgress = new JProgressBar(0, 100);

        productionPanel.add(productionUnitName);
        productionPanel.add(productionTurnsLeft);
        productionPanel.add(productionProgress);
        overlayLayer.add(productionPanel);

        JPanel researchPanel = new JPanel();
        researchPanel.setBackground(new Color(0x22, 0x22, 0x22, 220));
        researchPanel.setLayout(new BoxLayout(researchPanel, BoxLayout.Y_AXIS));
        researchPanel.setBounds(310, 700, 300, 68);
        researchPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel researchHeader = new JLabel("Research");
        researchHeader.setForeground(new Color(0xaaaaaa));
        researchStatusLabel = new JLabel("-");
        researchStatusLabel.setForeground(Color.WHITE);

        researchPanel.add(researchHeader);
        researchPanel.add(researchStatusLabel);
        overlayLayer.add(researchPanel);

        JButton endTurnButton = new JButton("End Turn");
        endTurnButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        endTurnButton.setBackground(new Color(0x5d4037));
        endTurnButton.setForeground(Color.WHITE);
        endTurnButton.setBounds(1200, 700, 150, 45);
        endTurnButton.addActionListener(e -> nextTurn());
        // phase 3: while an opponent is playing, the button says why it is off
        // (the screen rebuilds on every snapshot, so it comes back on by itself)
        if (MultiplayerSession.isActive() && !MultiplayerSession.isMyTurn()) {
            endTurnButton.setEnabled(false);
            endTurnButton.setToolTipText("It is " + MultiplayerSession.currentPlayerName() + "'s turn.");
        }
        overlayLayer.add(endTurnButton);
    }

    private void buildZoomControls() {
        JButton zoomInButton = new JButton("+");
        JButton zoomOutButton = new JButton("-");
        zoomInButton.setBounds(1300, 45, 30, 30);
        zoomOutButton.setBounds(1300, 80, 30, 30);
        zoomInButton.addActionListener(e -> zoomIn());
        zoomOutButton.addActionListener(e -> zoomOut());
        overlayLayer.add(zoomInButton);
        overlayLayer.add(zoomOutButton);
    }

    public void zoomIn() {
        if (zoomIndex < ZOOM_LEVELS.length - 1) {
            zoomIndex++;
            applyZoom();
        }
    }

    public void zoomOut() {
        if (zoomIndex > 0) {
            zoomIndex--;
            applyZoom();
        }
    }

    private void applyZoom() {
        double scale = ZOOM_LEVELS[zoomIndex];
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                graphicalMap[i][j].setZoom(scale);
            }
        }
        Dimension scaled = new Dimension((int) (mapBaseSize.width * scale), (int) (mapBaseSize.height * scale));
        mapPane.setPreferredSize(scaled);
        mapPane.revalidate();
        mapPane.repaint();
    }

    // ── Info panels ───────────────────────────────────────────────────────────

    public void showInfoPanel(JPanel[] rows) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(new Color(0x1e1e1e));
        for (JPanel row : rows) list.add(row);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(500, 500));
        showPopupPanel(scrollPane);
    }

    public void showUnits() {
        int unitsNumber = GameController.getCurrentTurnUser().getNation().getUnits().size();
        JPanel[] rows = new JPanel[unitsNumber];
        int i = 0;
        for (Unit unit : GameController.getCurrentTurnUser().getNation().getUnits()) {
            String addr = ((CivilizedUnit) unit).getCivilizedUnitType().imageAddress;
            var url = Civilization.class.getResource(addr);
            JLabel avatar = new JLabel();
            if (url != null) {
                Image img = new ImageIcon(url).getImage().getScaledInstance(70, 70, Image.SCALE_SMOOTH);
                avatar.setIcon(new ImageIcon(img));
            }
            JLabel unitType = new JLabel(((CivilizedUnit) unit).getCivilizedUnitType().name);
            unitType.setForeground(Color.WHITE);
            unitType.setFont(new Font("SansSerif", Font.BOLD, 13));
            JLabel unitLocation = new JLabel("(" + unit.getLocation().x + "," + unit.getLocation().y + ")");
            unitLocation.setForeground(new Color(0xaaaaaa));
            JLabel unitStatus = new JLabel(unit.getUnitStatus().name());
            unitStatus.setForeground(new Color(0xaaaaaa));

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
            row.setBackground(new Color(0x1e1e1e));
            row.setPreferredSize(new Dimension(480, 100));
            row.setMaximumSize(new Dimension(480, 100));
            row.add(avatar);
            row.add(unitType);
            row.add(unitLocation);
            row.add(unitStatus);
            rows[i++] = row;
        }
        showInfoPanel(rows);
    }

    public void showCities() { showTownHallPanel(); }

    public void showDiplomacies() { }
    public void showDiplomacyPanel(Nation nation) { }
    public void showEconomics() { }

    // ── Debug panel (for testing/demo: instant resources + fast turns) ───────

    public void showDebugPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(0x1e1e1e));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Debug / Demo tools");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setForeground(new Color(0xf0c060));
        content.add(header);

        JLabel hint = new JLabel("For testing: get resources instantly and skip turns.");
        hint.setForeground(new Color(0xaaaaaa));
        content.add(hint);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JButton res100 = new JButton("+100 all resources");
        JButton turn1 = new JButton("Skip 1 turn");
        JButton turn5 = new JButton("Skip 5 turns");
        JButton fillCap = new JButton("Fill units to cap");
        JButton reveal = new JButton("Reveal map");
        JButton happyUp = new JButton("Happiness +1");
        JButton happyDown = new JButton("Happiness -1");
        JButton quake = new JButton("Earthquake");
        for (JButton b : new JButton[]{res100, turn1, turn5, fillCap, reveal}) {
            b.setBackground(new Color(0x1a5276));
            b.setForeground(Color.WHITE);
            row.add(b);
        }

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setOpaque(false);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        for (JButton b : new JButton[]{happyUp, happyDown, quake}) {
            b.setBackground(new Color(0x1a5276));
            b.setForeground(Color.WHITE);
            row2.add(b);
        }

        // phase 2 combat: spawn something to fight right next to the Town Hall
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.setOpaque(false);
        row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JButton spawnBarbarian = new JButton("Spawn Barbarian");
        JButton spawnBear = new JButton("Spawn Bear");
        JButton spawnCamp = new JButton("Spawn Camp");
        for (JButton b : new JButton[]{spawnBarbarian, spawnBear, spawnCamp}) {
            b.setBackground(new Color(0x7a2020));
            b.setForeground(Color.WHITE);
            row3.add(b);
            b.addActionListener(e -> {
                String kind = b == spawnBarbarian ? "Barbarian" : b == spawnBear ? "Bear" : "Camp";
                String msg = CheatController.debugSpawnEnemy(kind);
                showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                updateWholeMap();
            });
        }
        res100.addActionListener(e -> {
            String msg = CheatController.debugAddAllResources(100);
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
            updateHud();
        });
        turn1.addActionListener(e -> {
            String msg = CheatController.debugAdvanceTurns(1);
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
            updateWholeMap();
            updateHud();
            showPendingEnemyBattles();
        });
        turn5.addActionListener(e -> {
            String msg = CheatController.debugAdvanceTurns(5);
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
            updateWholeMap();
            updateHud();
            showPendingEnemyBattles();
        });
        fillCap.addActionListener(e -> {
            String msg = CheatController.debugFillUnitsToCap();
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
            updateWholeMap();
            updateHud();
        });
        reveal.addActionListener(e -> {
            String msg = CheatController.debugRevealMap();
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
            updateWholeMap();
            updateHud();
        });
        happyUp.addActionListener(e -> {
            showPopUp(SwingUtilities.getWindowAncestor(root), CheatController.debugAdjustHappiness(1));
            updateHud();
        });
        happyDown.addActionListener(e -> {
            showPopUp(SwingUtilities.getWindowAncestor(root), CheatController.debugAdjustHappiness(-1));
            updateHud();
        });
        quake.addActionListener(e -> {
            String msg = CheatController.debugTriggerEarthquake();
            closeActivePopup(); // get the debug panel out of the way so the shake is visible
            updateWholeMap();
            updateHud();
            if (DisasterController.lastQuakeArea != null) {
                playEarthquakeAnimation(DisasterController.lastQuakeArea);
                DisasterController.lastQuakeArea = null;
                DisasterController.lastQuakeCenter = null;
            }
            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
        });
        content.add(row);
        content.add(row2);
        content.add(row3);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setPreferredSize(new Dimension(580, 210));
        showPopupPanel(scrollPane);
    }

    // ── Trade panel (Phase 2: Bazaar + Trading Post) ─────────────────────────

    // last dropdown choices, kept across panel reopens
    private ResourceType lastTradeSell = ResourceType.FOOD;
    private ResourceType lastTradeReceive = ResourceType.WOOD;

    public void showTradePanel() {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        boolean hasBazaar = TradeController.hasBazaar(nation);
        boolean hasPost = TradeController.hasTradingPost(nation);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(0x1e1e1e));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Trade");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setForeground(new Color(0xf0c060));
        content.add(header);

        // shared sell/receive pickers - remember the player's last choice so the
        // selection survives the panel reopening after each trade
        JComboBox<ResourceType> sellBox = new JComboBox<>(ResourceType.values());
        JComboBox<ResourceType> receiveBox = new JComboBox<>(ResourceType.values());
        sellBox.setSelectedItem(lastTradeSell);
        receiveBox.setSelectedItem(lastTradeReceive);
        sellBox.addActionListener(e -> lastTradeSell = (ResourceType) sellBox.getSelectedItem());
        receiveBox.addActionListener(e -> lastTradeReceive = (ResourceType) receiveBox.getSelectedItem());
        JPanel pickers = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pickers.setOpaque(false);
        JLabel sellLbl = new JLabel("Sell:");
        sellLbl.setForeground(Color.WHITE);
        JLabel recvLbl = new JLabel("Receive:");
        recvLbl.setForeground(Color.WHITE);
        pickers.add(sellLbl);
        pickers.add(sellBox);
        pickers.add(recvLbl);
        pickers.add(receiveBox);
        pickers.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        content.add(pickers);

        // ── Bazaar section ──────────────────────────────────────────────────
        JLabel bazaarHeader = new JLabel("Bazaar - sell exactly 10 (50%), 100 (60%) or 500 (70%). One trade per turn.");
        bazaarHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        bazaarHeader.setForeground(new Color(0xaaaaaa));
        bazaarHeader.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        content.add(bazaarHeader);

        if (!hasBazaar) {
            JLabel none = new JLabel("No Bazaar yet - build one with a Builder (needs a level 2 Town Hall).");
            none.setForeground(Color.WHITE);
            content.add(none);
        } else if (nation.isBazaarUsedThisTurn()) {
            JLabel used = new JLabel("The Bazaar already traded this turn.");
            used.setForeground(Color.ORANGE);
            content.add(used);
        } else {
            JPanel bazaarRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bazaarRow.setOpaque(false);
            bazaarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            for (int amount : new int[]{10, 100, 500}) {
                int rate = (int) (TradeController.bazaarRateFor(amount) * 100);
                JButton b = new JButton("Sell " + amount + " @" + rate + "%");
                b.setBackground(new Color(0x5d4037));
                b.setForeground(Color.WHITE);
                b.addActionListener(e -> {
                    String msg = TradeController.tradeAtBazaar(
                            (ResourceType) sellBox.getSelectedItem(),
                            (ResourceType) receiveBox.getSelectedItem(), amount);
                    showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                    updateHud();
                    closeActivePopup();
                    showTradePanel();
                });
                bazaarRow.add(b);
            }
            content.add(bazaarRow);
        }

        // ── Trading Post section ────────────────────────────────────────────
        JLabel postHeader = new JLabel("Trading Post - any amount at 80%. One trade per turn. Must be inside your territory.");
        postHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        postHeader.setForeground(new Color(0xaaaaaa));
        postHeader.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        content.add(postHeader);

        if (!hasPost) {
            JLabel none = new JLabel("No Trading Post inside your territory - expand your borders to reach one.");
            none.setForeground(Color.WHITE);
            content.add(none);
        } else if (nation.isTradingPostUsedThisTurn()) {
            JLabel used = new JLabel("The Trading Post already traded this turn.");
            used.setForeground(Color.ORANGE);
            content.add(used);
        } else {
            JPanel postRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            postRow.setOpaque(false);
            postRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            JLabel amountLbl = new JLabel("Amount:");
            amountLbl.setForeground(Color.WHITE);
            JTextField amountField = new JTextField("10", 5);
            JButton tradeBtn = new JButton("Trade @80%");
            tradeBtn.setBackground(new Color(0x5d4037));
            tradeBtn.setForeground(Color.WHITE);
            tradeBtn.addActionListener(e -> {
                int amount;
                try {
                    amount = Integer.parseInt(amountField.getText().trim());
                } catch (NumberFormatException ex) {
                    showPopUp(SwingUtilities.getWindowAncestor(root), "Enter a valid number to trade!");
                    return;
                }
                String msg = TradeController.tradeAtTradingPost(
                        (ResourceType) sellBox.getSelectedItem(),
                        (ResourceType) receiveBox.getSelectedItem(), amount);
                showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                updateHud();
                closeActivePopup();
                showTradePanel();
            });
            postRow.add(amountLbl);
            postRow.add(amountField);
            postRow.add(tradeBtn);
            content.add(postRow);
        }

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setPreferredSize(new Dimension(660, 340));
        showPopupPanel(scrollPane);
    }

    public void showDemographics() {
        int nationNumber = Game.instance.getPlayersInGame().size();
        JPanel[] rows = new JPanel[nationNumber];
        int i = 0;
        for (User user : Game.instance.getPlayersInGame()) {
            Nation nation = user.getNation();
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            row.setBackground(new Color(0x1e1e1e));
            row.setPreferredSize(new Dimension(480, 60));
            row.setMaximumSize(new Dimension(480, 60));

            JLabel nationName = new JLabel("Player: " + user.getUsername());
            nationName.setForeground(Color.WHITE);
            nationName.setFont(new Font("SansSerif", Font.BOLD, 13));
            // phase 3: an opponent's economy is secret - the server does not even
            // send their balances (they arrive zeroed), so show "?" instead of
            // pretending we know. Unit/territory counts reflect only what our fog
            // reveals, which is exactly what the spec allows us to know
            boolean economyHidden = MultiplayerSession.isActive()
                    && nation != Game.instance.getLoggedInUser().getNation();
            JLabel units = new JLabel("Units: " + (economyHidden
                    ? nation.getUnits().size() + " visible"
                    : nation.getUnits().size() + "/" + nation.getUnitCap()));
            JLabel territory = new JLabel("Territory: "
                    + (economyHidden ? nation.getTerritory().size() + " seen" : nation.getTerritory().size() + " hexes"));
            JLabel food = new JLabel("Food: " + (economyHidden ? "?" : nation.getFood().getBalance()));
            JLabel wood = new JLabel("Wood: " + (economyHidden ? "?" : nation.getWood().getBalance()));
            JLabel stone = new JLabel("Stone: " + (economyHidden ? "?" : nation.getStone().getBalance()));
            JLabel iron = new JLabel("Iron: " + (economyHidden ? "?" : nation.getIron().getBalance()));
            for (JLabel l : new JLabel[]{units, territory, food, wood, stone, iron}) l.setForeground(new Color(0xaaaaaa));

            row.add(nationName);
            row.add(units);
            row.add(territory);
            row.add(food);
            row.add(wood);
            row.add(stone);
            row.add(iron);
            rows[i++] = row;
        }
        showInfoPanel(rows);
    }

    private JWindow activePopup;

    /** Re-centers the open popup when the game window is moved/resized/maximized. */
    private java.awt.event.ComponentAdapter popupFollower;
    private Window popupOwnerWindow;

    private void showPopupPanel(JComponent content) {
        closeActivePopup();
        Window window = SwingUtilities.getWindowAncestor(root);

        // never let the popup be taller/wider than the game window itself -
        // in a small window the content just scrolls instead of getting cut off
        if (window != null && content.getPreferredSize() != null) {
            Dimension pref = content.getPreferredSize();
            content.setPreferredSize(new Dimension(
                    Math.min(pref.width, window.getWidth() - 80),
                    Math.min(pref.height, window.getHeight() - 140)));
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(0x212121));
        wrapper.setBorder(BorderFactory.createLineBorder(new Color(0xf0c060), 2));

        JLabel ex = exCreator();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.setOpaque(false);
        top.add(ex);
        ex.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                closeActivePopup();
            }
        });

        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);

        activePopup = new JWindow(window);
        activePopup.getContentPane().add(wrapper);
        activePopup.pack();
        if (window != null) {
            activePopup.setLocation(window.getX() + (window.getWidth() - activePopup.getWidth()) / 2,
                    window.getY() + (window.getHeight() - activePopup.getHeight()) / 2);
        }

        // drag anywhere on the popup's dark background to move it around
        MouseAdapter dragger = new MouseAdapter() {
            private Point grabOffset;

            @Override
            public void mousePressed(MouseEvent e) {
                grabOffset = e.getLocationOnScreen();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grabOffset == null || activePopup == null) return;
                Point now = e.getLocationOnScreen();
                Point loc = activePopup.getLocation();
                activePopup.setLocation(loc.x + now.x - grabOffset.x, loc.y + now.y - grabOffset.y);
                grabOffset = now;
            }
        };
        wrapper.addMouseListener(dragger);
        wrapper.addMouseMotionListener(dragger);
        top.addMouseListener(dragger);
        top.addMouseMotionListener(dragger);

        // maximizing/resizing the game window re-centers the popup so it never
        // gets stranded off to one side or stuck outside the visible area
        if (window != null) {
            popupOwnerWindow = window;
            popupFollower = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    recenterPopup(window);
                }

                @Override
                public void componentMoved(java.awt.event.ComponentEvent e) {
                    recenterPopup(window);
                }
            };
            window.addComponentListener(popupFollower);
        }

        activePopup.setVisible(true);
    }

    private void recenterPopup(Window window) {
        if (activePopup == null || window == null) return;
        activePopup.setLocation(window.getX() + (window.getWidth() - activePopup.getWidth()) / 2,
                window.getY() + (window.getHeight() - activePopup.getHeight()) / 2);
    }

    private void closeActivePopup() {
        if (popupOwnerWindow != null && popupFollower != null) {
            popupOwnerWindow.removeComponentListener(popupFollower);
            popupFollower = null;
            popupOwnerWindow = null;
        }
        if (activePopup != null) {
            activePopup.dispose();
            activePopup = null;
        }
        // A Move click-handler armed by unitMove() only gets cleared once a destination
        // tile is actually clicked; if the popup is closed first (X button, Menu, etc.)
        // it would otherwise stay armed and swallow every future map click.
        clearMoveHandlers();
    }

    public void showSelectedCivilizedUnitInfo() {
        closeActivePopup();
        openImprovementsScrollPane = null;
        CivilizedUnit civilizedUnit = GameController.getSelectedCivilizedUnit();
        CivilizedUnitType civilizedUnitType = civilizedUnit.getCivilizedUnitType();

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(0x212121));
        wrapper.setBorder(BorderFactory.createLineBorder(new Color(0xf0c060), 2));

        JLabel ex = exCreator();
        ex.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                closeActivePopup();
                GameController.setSelectedCivilizedUnit(null);
            }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.setOpaque(false);
        top.add(ex);

        JLabel unitImage = new JLabel();
        var url = Civilization.class.getResource(civilizedUnitType.imageAddress);
        if (url != null) {
            Image img = new ImageIcon(url).getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
            unitImage.setIcon(new ImageIcon(img));
        }

        JLabel unitName = new JLabel(civilizedUnitType.name);
        unitName.setForeground(new Color(0xf0c060));
        unitName.setFont(new Font("SansSerif", Font.BOLD, 18));

        JLabel unitMovement = new JLabel("AP: " + civilizedUnit.getCurrentAp() + "/" + civilizedUnit.getMaxAp());
        unitMovement.setForeground(Color.WHITE);
        JLabel unitHP = new JLabel("Health: " + civilizedUnit.getHp() + "/" + civilizedUnitType.maxHp);
        unitHP.setForeground(Color.WHITE);

        JPanel infoVBox = new JPanel();
        infoVBox.setOpaque(false);
        infoVBox.setLayout(new BoxLayout(infoVBox, BoxLayout.Y_AXIS));
        infoVBox.add(unitHP);
        infoVBox.add(unitMovement);
        if (civilizedUnitType == CivilizedUnitType.BUILDER) {
            JLabel unitCharges = new JLabel("Charges: " + civilizedUnit.getCharges());
            unitCharges.setForeground(civilizedUnit.getCharges() <= 1 ? new Color(0xe0685e) : Color.WHITE);
            infoVBox.add(unitCharges);
        }

        JPanel infoHBox = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoHBox.setOpaque(false);
        infoHBox.add(unitImage);
        infoHBox.add(infoVBox);

        JPanel wholeUnit = new JPanel();
        wholeUnit.setBackground(new Color(0x212121));
        wholeUnit.setLayout(new BoxLayout(wholeUnit, BoxLayout.Y_AXIS));
        wholeUnit.setPreferredSize(new Dimension(380, 230));
        wholeUnit.add(unitName);
        wholeUnit.add(infoHBox);

        JPanel unitActions = new JPanel();
        unitActions.setLayout(new BoxLayout(unitActions, BoxLayout.Y_AXIS));
        unitActions.setBackground(new Color(0x212121));

        JPanel actionsAndImprovementsHBox = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionsAndImprovementsHBox.setBackground(new Color(0x212121));

        for (UnitActions action : civilizedUnit.getCivilizedUnitType().actions) {
            JButton actionImage = new JButton(action.label, scaledIcon(action.icon, 28, 28));
            actionImage.setToolTipText(action.label);
            actionImage.setHorizontalAlignment(SwingConstants.LEFT);
            actionImage.setIconTextGap(8);
            actionImage.setForeground(Color.WHITE);
            actionImage.setBorderPainted(false);
            actionImage.setContentAreaFilled(false);
            actionImage.setFont(new Font("SansSerif", Font.PLAIN, 11));
            actionImage.setMaximumSize(new Dimension(150, 34));
            actionImage.setAlignmentX(Component.LEFT_ALIGNMENT);

            handleCommonActions(action, civilizedUnit, unitMovement, 0, actionImage);
            handleCivilizedUnitActions(action, civilizedUnit, actionImage, actionsAndImprovementsHBox);

            unitActions.add(actionImage);
            unitActions.add(Box.createVerticalStrut(5));
        }

        JScrollPane actionsScrollPane = new JScrollPane(unitActions);
        actionsScrollPane.setPreferredSize(new Dimension(160, 230));
        actionsScrollPane.setBackground(new Color(0x212121));
        actionsAndImprovementsHBox.add(actionsScrollPane);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(new Color(0x212121));
        center.add(wholeUnit, BorderLayout.CENTER);
        center.add(actionsAndImprovementsHBox, BorderLayout.EAST);

        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(center, BorderLayout.CENTER);

        Window window = SwingUtilities.getWindowAncestor(root);
        activePopup = new JWindow(window);
        activePopup.getContentPane().add(wrapper);
        activePopup.pack();
        if (window != null) {
            activePopup.setLocation(window.getX(), window.getY() + window.getHeight() - activePopup.getHeight() - 60);
        }
        activePopup.setVisible(true);
    }

    private ImageIcon scaledIcon(ImageIcon icon, int w, int h) {
        if (icon == null || icon.getImage() == null) return icon;
        return new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }

    /** The unit actions that are wired through the server in multiplayer. */
    private static boolean multiplayerAction(UnitActions action) {
        return action == UnitActions.MOVE || action == UnitActions.ATTACK
                || action == UnitActions.BUILD_IMPROVEMENT || action == UnitActions.REPAIR;
    }

    private void handleCommonActions(UnitActions action, Unit unit, JLabel unitMovement, int selection, JButton actionButton) {
        // phase 3: these actions go through the server; the rest is disabled
        // with the reason on the tooltip (never a dead mystery button)
        if (MultiplayerSession.isActive() && !multiplayerAction(action)) {
            actionButton.setEnabled(false);
            actionButton.setToolTipText("Not available in multiplayer yet.");
            return;
        }
        switch (action) {
            case SLEEP:
                actionButton.addActionListener(e -> {
                    String message = UnitController.unitSleep();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                });
                break;
            case WAKE:
                actionButton.addActionListener(e -> {
                    String message = UnitController.unitWake();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                });
                break;
            case MOVE:
                boolean canMove = unit.hasAp() && unit.getUnitStatus() != UnitStatus.WORKING;
                actionButton.setEnabled(canMove);
                if (canMove) {
                    actionButton.addActionListener(e -> unitMove(unitMovement, unit, selection));
                }
                break;
        }
    }

    /** The improvement-list scrollpane currently appended to the open unit popup, if any. */
    private JScrollPane openImprovementsScrollPane;

    private void handleCivilizedUnitActions(UnitActions action, CivilizedUnit unit, JButton actionButton, JPanel actionsAndImprovementsHBox) {
        // phase 3: most of these don't run locally in multiplayer. MOVE must be
        // left alone here - every button passes through BOTH handlers, and MOVE
        // belongs to handleCommonActions (which already enabled it for multiplayer).
        // ATTACK / BUILD_IMPROVEMENT / REPAIR fall through to their cases below,
        // which each have their own multiplayer branch
        if (MultiplayerSession.isActive() && !multiplayerAction(action)) {
            actionButton.setEnabled(false);
            actionButton.setToolTipText("Not available in multiplayer yet.");
            return;
        }
        switch (action) {
            case FOUND_CITY:
                boolean canExpand = TownHallController.canExpandBorder(unit);
                actionButton.setEnabled(canExpand);
                if (canExpand) {
                    actionButton.addActionListener(e -> {
                        String message = TownHallController.expandBorder();
                        showPopUp(SwingUtilities.getWindowAncestor(root), message);
                        updateWholeMap();
                        updateHud();
                        if (GameController.getSelectedCivilizedUnit() == unit) showSelectedCivilizedUnitInfo();
                    });
                }
                break;
            case BUILD_IMPROVEMENT:
                // phase 3: in multiplayer the build is a REQUEST - pick a type,
                // the server validates terrain/tech/resources/AP/charges itself
                if (MultiplayerSession.isActive()) {
                    actionButton.addActionListener(e -> {
                        ImprovementType[] types = ImprovementType.values();
                        String[] options = new String[types.length];
                        for (int i = 0; i < types.length; i++)
                            options[i] = types[i].name + "  (" + types[i].buildCostWood + "w/"
                                    + types[i].buildCostStone + "s/" + types[i].buildCostIron + "i)";
                        int pick = JOptionPane.showOptionDialog(root, "Build what on this hex?", "Build",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (pick < 0) return;
                        MultiplayerSession.get().sendBuildImprovement(unit.getLocation(), types[pick].name);
                    });
                    break;
                }
                actionButton.addActionListener(e -> {
                    if (openImprovementsScrollPane != null) {
                        actionsAndImprovementsHBox.remove(openImprovementsScrollPane);
                    }
                    openImprovementsScrollPane = buildImprovement();
                    actionsAndImprovementsHBox.add(openImprovementsScrollPane);
                    actionsAndImprovementsHBox.revalidate();
                    actionsAndImprovementsHBox.repaint();
                    activePopup.pack();
                });
                break;
            case REPAIR:
                // phase 3: station/unstation is a server request in multiplayer
                if (MultiplayerSession.isActive()) {
                    boolean stationed = unit.isStationed();
                    actionButton.setText(stationed ? "Unstation" : "Station");
                    actionButton.addActionListener(e ->
                            MultiplayerSession.get().sendStation(unit.getLocation(), !stationed));
                    break;
                }
                if (unit.isStationed()) {
                    // Unstationing always succeeds once a Worker is confirmed stationed.
                    actionButton.setEnabled(true);
                    actionButton.setText("Unstation");
                } else {
                    String blocked = WorkerController.checkCanStation();
                    actionButton.setEnabled(blocked == null);
                    actionButton.setText("Station");
                    if (blocked != null) actionButton.setToolTipText(blocked);
                }
                actionButton.addActionListener(e -> {
                    CivilizedUnit wu = GameController.getSelectedCivilizedUnit();
                    String message = (wu != null && wu.isStationed())
                            ? WorkerController.unstationWorker()
                            : WorkerController.stationWorker();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                    updateHud();
                    // need to manually repaint the tile here since stationing doesn't move
                    // the unit - otherwise the green "stationed" dot doesn't show up until
                    // something else forces the map to redraw
                    if (wu != null) graphicalMap[wu.getLocation().x][wu.getLocation().y].updateMap();
                    if (wu != null && GameController.getSelectedCivilizedUnit() == wu) showSelectedCivilizedUnitInfo();
                });
                break;
            case REMOVE_FEATURE:
                actionButton.addActionListener(e -> {
                    String message = WorkerController.setWorkerToRemoveFeature();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                });
                break;
            case BUILD_ROAD: {
                String roadBlocked = WorkerController.checkCanBuildRoad();
                actionButton.setEnabled(roadBlocked == null);
                if (roadBlocked != null) actionButton.setToolTipText(roadBlocked);
                actionButton.addActionListener(e -> {
                    String message = WorkerController.buildRoad();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                    updateWholeMap();
                    updateHud();
                    if (GameController.getSelectedCivilizedUnit() == unit) showSelectedCivilizedUnitInfo();
                });
                break;
            }
            case ATTACK: {
                // phase 3: in multiplayer the attack is a REQUEST - the server rolls
                // the dice and everyone gets fresh snapshots plus a war report
                if (MultiplayerSession.isActive()) {
                    wireMultiplayerAttack(actionButton, unit);
                    break;
                }
                // phase 2 dice combat (P2-14/15): pick a target in range, fight, and
                // show the dice - the battle must never resolve silently
                String blocked = CombatController.checkCanAttack(unit);
                actionButton.setEnabled(blocked == null);
                if (blocked != null) actionButton.setToolTipText(blocked);
                actionButton.addActionListener(e -> {
                    java.util.ArrayList<Pair<Integer, Integer>> targets = CombatController.getTargetsInRange(unit);
                    if (targets.isEmpty()) return;
                    Pair<Integer, Integer> picked = targets.get(0);
                    if (targets.size() > 1) {
                        String[] options = new String[targets.size()];
                        for (int i = 0; i < targets.size(); i++) {
                            Pair<Integer, Integer> t = targets.get(i);
                            options[i] = Game.instance.map[t.x][t.y].getEnemy().getType().name
                                    + " at (" + t.x + "," + t.y + ")";
                        }
                        int pick = JOptionPane.showOptionDialog(root, "Attack which enemy?", "Attack",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (pick < 0) return;
                        picked = targets.get(pick);
                    }
                    String targetBlocked = CombatController.checkCanAttackTarget(unit, picked);
                    if (targetBlocked != null) {
                        showPopUp(SwingUtilities.getWindowAncestor(root), targetBlocked);
                        return;
                    }
                    CombatController.BattleReport report = CombatController.attack(unit, picked);
                    showBattleDialog(report);
                    updateWholeMap();
                    updateHud();
                    // the attacker may have died in the battle - only refresh its popup if it lives
                    if (GameController.getSelectedCivilizedUnit() == unit) showSelectedCivilizedUnitInfo();
                    else closeActivePopup();
                });
                break;
            }
            case DEMOLISH: {
                String blocked = WorkerController.checkCanDemolish();
                actionButton.setEnabled(blocked == null);
                if (blocked != null) actionButton.setToolTipText(blocked);
                actionButton.addActionListener(e -> {
                    Land target = WorkerController.findDemolishTarget();
                    String buildingName = (target != null && target.getImprovement() != null)
                            ? target.getImprovement().getImprovementType().name : "building";
                    int confirm = JOptionPane.showConfirmDialog(root,
                            "Demolish the " + buildingName + "?\nBuild costs are NOT refunded and stationed workers are evicted.",
                            "Demolish building", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) return;
                    String message = WorkerController.demolishImprovement();
                    showPopUp(SwingUtilities.getWindowAncestor(root), message);
                    updateWholeMap();
                    updateHud();
                    if (GameController.getSelectedCivilizedUnit() == unit) showSelectedCivilizedUnitInfo();
                });
                break;
            }
            default:
                break;
        }
    }

    public void showTechnologyPanel() { showTownHallPanel(); }

    // ── Town Hall panel ───────────────────────────────────────────────────────

    public void showTownHallPanel() {
        Nation nation = GameController.getCurrentTurnUser().getNation();
        TownHall townHall = nation.getTownHall();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(0x1e1e1e));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        // no fixed preferred size! with one hard-coded, the scroll pane thought the
        // content ended at 500px and silently clipped the Archer/Cavalry train rows

        JLabel header = new JLabel(townHall == null ? "Town Hall"
                : townHall.getLevelName() + " (Level " + townHall.getLevel() + ")");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setForeground(new Color(0xf0c060));
        content.add(header);
        if (townHall != null) {
            JLabel hpLabel = new JLabel("HP " + townHall.getHp() + "/" + townHall.getMaxHp());
            hpLabel.setForeground(townHall.getHp() < townHall.getMaxHp() / 2 ? Color.ORANGE : new Color(0x7ecf5a));
            content.add(hpLabel);
        }

        if (townHall == null) {
            JLabel noTh = new JLabel("No Town Hall placed yet.");
            noTh.setForeground(Color.WHITE);
            content.add(noTh);
        } else {
            // ── Phase 2: production queue status + cancel ────────────────────
            JLabel queueHeader = new JLabel("Production Queue");
            queueHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
            queueHeader.setForeground(new Color(0xaaaaaa));
            queueHeader.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
            content.add(queueHeader);

            if (!townHall.hasQueuedProduction()) {
                JLabel empty = new JLabel("Queue is empty.");
                empty.setForeground(Color.WHITE);
                content.add(empty);
            } else {
                int idx = 0;
                for (ProductionOrder order : townHall.getProductionQueue()) {
                    JPanel row = new JPanel(new BorderLayout(10, 0));
                    row.setBackground(new Color(0x2a2a2a));
                    row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                    JLabel orderLabel = new JLabel((idx == 0 ? "▶ " : (idx + 1) + ". ")
                            + order.getDisplayName() + "  (" + order.getTurnsLeft() + " turns left)");
                    orderLabel.setForeground(idx == 0 ? new Color(0x7ecf5a) : Color.WHITE);
                    row.add(orderLabel, BorderLayout.CENTER);

                    if (idx == 0) {
                        JButton cancelBtn = new JButton("Cancel");
                        cancelBtn.setBackground(new Color(0x7a2b2b));
                        cancelBtn.setForeground(Color.WHITE);
                        cancelBtn.setToolTipText("Cancels this order. Spent resources are NOT refunded!");
                        cancelBtn.addActionListener(e -> {
                            int confirm = JOptionPane.showConfirmDialog(root,
                                    "Cancel " + order.getDisplayName() + "?\nSpent resources will NOT be refunded.",
                                    "Cancel production", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (confirm != JOptionPane.YES_OPTION) return;
                            String msg = TownHallController.cancelCurrentProduction();
                            showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                            updateHud();
                            closeActivePopup();
                            showTownHallPanel();
                        });
                        row.add(cancelBtn, BorderLayout.EAST);
                    }
                    content.add(row);
                    idx++;
                }
            }

            // ── Phase 2: town hall upgrade ───────────────────────────────────
            if (townHall.getLevel() < TownHall.MAX_LEVEL) {
                int target = townHall.getLevel() + 1;
                int upWood  = (target == 2) ? TownHall.L2_WOOD  : TownHall.L3_WOOD;
                int upStone = (target == 2) ? TownHall.L2_STONE : TownHall.L3_STONE;
                int upIron  = (target == 2) ? TownHall.L2_IRON  : TownHall.L3_IRON;
                int upTurns = (target == 2) ? TownHall.L2_TURNS : TownHall.L3_TURNS;

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setBackground(new Color(0x2a2a2a));
                row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel upLabel = new JLabel("Upgrade to " + (target == 2 ? "Settlement" : "Capital"));
                upLabel.setForeground(Color.WHITE);
                JLabel upCost = new JLabel((upWood > 0 ? "Wood:" + upWood + " " : "")
                        + (upStone > 0 ? "Stone:" + upStone + " " : "")
                        + (upIron > 0 ? "Iron:" + upIron + " " : "") + "| " + upTurns + " turns");
                upCost.setForeground(new Color(0xaaaaaa));

                boolean upAffordable = nation.canAfford(0, upWood, upStone, upIron);
                boolean upQueued = townHall.isUpgradeQueued();
                JButton upBtn = new JButton(upQueued ? "In Progress"
                        : townHall.isBusy() ? "TH Busy" : upAffordable ? "Upgrade" : "Can't Afford");
                upBtn.setEnabled(!townHall.isBusy() && upAffordable);
                upBtn.setBackground(upQueued ? new Color(0x444444) : new Color(0x1a5276));
                upBtn.setForeground(Color.WHITE);
                upBtn.addActionListener(e -> {
                    String msg = TownHallController.upgradeTownHall();
                    showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                    updateHud();
                    closeActivePopup();
                    showTownHallPanel();
                });

                JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT));
                labels.setOpaque(false);
                labels.add(upLabel);
                labels.add(upCost);
                row.add(labels, BorderLayout.CENTER);
                row.add(upBtn, BorderLayout.EAST);
                content.add(row);
            }

            content.add(new JSeparator());

            JLabel techHeader = new JLabel("Research");
            techHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
            techHeader.setForeground(new Color(0xaaaaaa));
            techHeader.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
            content.add(techHeader);

            for (TechnologyType tech : TechnologyType.values()) {
                boolean done = nation.hasTechnology(tech);
                boolean available = tech.isAvailable(nation.getTechnologies());

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setBackground(new Color(0x2a2a2a));
                row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel nameLabel = new JLabel(tech.name);
                nameLabel.setForeground(Color.WHITE);

                JLabel costLabel = new JLabel(costString(tech));
                costLabel.setForeground(new Color(0xaaaaaa));

                boolean affordable = nation.canAfford(0, tech.woodCost, tech.stoneCost, tech.ironCost);
                boolean queued = townHall.isAlreadyQueued(tech);
                boolean levelOk = townHall.getLevel() >= tech.requiredTownHallLevel;
                boolean researchable = !done && !queued && available && affordable && levelOk && !townHall.isBusy();

                JButton btn = new JButton(done ? "Done" : queued ? "In Progress"
                        : !available ? "Locked" : !levelOk ? "TH Lv." + tech.requiredTownHallLevel
                        : townHall.isBusy() ? "TH Busy"
                        : !affordable ? "Can't Afford" : "Research");
                btn.setEnabled(researchable);
                btn.setBackground(done ? new Color(0x2d6a2d) : researchable ? new Color(0x1a5276) : new Color(0x444444));
                btn.setForeground(Color.WHITE);
                if (!done && available && !affordable) {
                    btn.setToolTipText("Not enough resources: need " + costString(tech).trim());
                }
                if (!done && available && !levelOk) {
                    btn.setToolTipText(tech.name + " needs Town Hall level " + tech.requiredTownHallLevel);
                }

                btn.addActionListener(e -> {
                    String msg = TownHallController.researchTechnology(tech.name);
                    showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                    updateHud();
                    closeActivePopup();
                    showTownHallPanel();
                });

                JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT));
                labels.setOpaque(false);
                labels.add(nameLabel);
                labels.add(costLabel);
                row.add(labels, BorderLayout.CENTER);
                row.add(btn, BorderLayout.EAST);
                content.add(row);
            }

            content.add(new JSeparator());

            JLabel trainHeader = new JLabel("Train Unit");
            trainHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
            trainHeader.setForeground(new Color(0xaaaaaa));
            trainHeader.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            content.add(trainHeader);

            for (CivilizedUnitType type : CivilizedUnitType.values()) {
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setBackground(new Color(0x2a2a2a));
                row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel nameLabel = new JLabel(type.name);
                nameLabel.setForeground(type.isMilitary ? new Color(0xe57373) : Color.WHITE);
                if (type.isMilitary) {
                    nameLabel.setToolTipText("HP " + type.maxHp + ", AP " + type.ap
                            + ", Range " + type.attackRange + ", Attack " + type.attackPower);
                }

                JLabel costLabel = new JLabel("Food: " + type.productionCostFood
                        + (type.productionCostWood > 0 ? "  Wood: " + type.productionCostWood : "")
                        + "  |  " + type.turnsToTrain + " turns");
                costLabel.setForeground(new Color(0xaaaaaa));

                // phase 2 military gates: TH level, military stable (cavalry), military cap
                boolean capFull = !nation.canTrainUnit()
                        || (type.isMilitary && !nation.canTrainMilitaryUnit());
                boolean levelOk = townHall.getLevel() >= type.requiredTownHallLevel;
                boolean stableOk = !type.requiresMilitaryStable
                        || nation.hasImprovementInTerritory(ImprovementType.MILITARY_STABLE);
                boolean affordable = nation.getFood().getBalance() >= type.productionCostFood
                        && nation.getWood().getBalance() >= type.productionCostWood;

                JButton btn = new JButton(!levelOk ? "TH Lv." + type.requiredTownHallLevel
                        : !stableOk ? "Needs Stable"
                        : capFull ? "At Cap"
                        : townHall.isBusy() ? "TH Busy"
                        : !affordable ? "Can't Afford" : "Train");
                btn.setEnabled(levelOk && stableOk && !capFull && !townHall.isBusy() && affordable);
                if (type.isMilitary && capFull) {
                    btn.setToolTipText("Military cap: " + nation.getMilitaryUnitCount()
                            + "/" + nation.getMilitaryUnitCap() + " (5 per Town Hall level)");
                }
                btn.setBackground(capFull ? new Color(0x444444) : new Color(0x5d4037));
                btn.setForeground(Color.WHITE);

                btn.addActionListener(e -> {
                    String msg = TownHallController.enqueueUnit(type.name);
                    showPopUp(SwingUtilities.getWindowAncestor(root), msg);
                    updateHud();
                    closeActivePopup();
                    showTownHallPanel();
                });

                JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT));
                labels.setOpaque(false);
                labels.add(nameLabel);
                labels.add(costLabel);
                row.add(labels, BorderLayout.CENTER);
                row.add(btn, BorderLayout.EAST);
                content.add(row);
            }
        }

        JScrollPane scrollPane = new JScrollPane(content);
        // viewport size only - the content is taller and scrolls inside it
        scrollPane.setPreferredSize(new Dimension(500, 780));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        showPopupPanel(scrollPane);
    }

    private String costString(TechnologyType tech) {
        StringBuilder sb = new StringBuilder();
        if (tech.woodCost > 0) sb.append("Wood:").append(tech.woodCost).append(" ");
        if (tech.stoneCost > 0) sb.append("Stone:").append(tech.stoneCost).append(" ");
        if (tech.ironCost > 0) sb.append("Iron:").append(tech.ironCost);
        return sb.toString().trim();
    }

    public void showMenu() {
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(new Color(0x1e1e1e));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JButton continueButton = new JButton("Continue");
        continueButton.addActionListener(e -> closeActivePopup());

        // phase 2 save/load: both live in the pause menu too (spec wants load
        // reachable from the main menu AND from inside the game).
        // phase 3: in multiplayer only the SERVER saves (it autosaves each round),
        // and loading a local file mid-match makes no sense at all
        JButton saveButton = new JButton("Save Game");
        saveButton.addActionListener(e -> { if (!blockInMultiplayer()) saveGameDialog(); });

        JButton loadButton = new JButton("Load Game");
        loadButton.addActionListener(e -> { if (!blockInMultiplayer()) loadGameDialog(); });

        JButton returnToMainMenu = new JButton("Return To Main Menu");
        returnToMainMenu.addActionListener(e -> {
            closeActivePopup();
            if (MultiplayerSession.isActive()) {
                MultiplayerSession.get().end("You left the game.");
            } else {
                Game.instance.changeScene(Menus.MAIN_MENU);
            }
        });

        JButton exit = new JButton("Exit");
        exit.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(root, "Are you sure you want to exit?",
                    "Exit", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                MainController.stopMusic();
                System.exit(0);
            }
        });

        for (JButton b : new JButton[]{continueButton, saveButton, loadButton, returnToMainMenu, exit}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(220, 40));
            menuPanel.add(Box.createVerticalStrut(15));
            menuPanel.add(b);
        }

        GameController.setSelectedCivilizedUnit(null);
        showPopupPanel(menuPanel);
    }

    private void saveGameDialog() {
        String[] options = new String[SaveController.SLOTS.length];
        for (int i = 0; i < options.length; i++) options[i] = SaveController.describeSlot(SaveController.SLOTS[i]);
        int pick = JOptionPane.showOptionDialog(root, "Choose a save slot (its old content is overwritten):",
                "Save Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (pick < 0) return;
        String name = JOptionPane.showInputDialog(root, "Name for this save (optional):", "");
        if (name == null) return; // cancelled
        showPopUp(SwingUtilities.getWindowAncestor(root), SaveController.saveGame(SaveController.SLOTS[pick], name));
    }

    private void loadGameDialog() {
        String[] slots = {SaveController.SLOTS[0], SaveController.SLOTS[1], SaveController.SLOTS[2], SaveController.AUTOSAVE_SLOT};
        String[] options = new String[slots.length];
        for (int i = 0; i < slots.length; i++) options[i] = SaveController.describeSlot(slots[i]);
        int pick = JOptionPane.showOptionDialog(root, "Choose a slot to load:",
                "Load Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (pick < 0) return;

        // spec: a damaged slot must be marked and the player must be able to delete
        // it (or just fall back to the autosave, which is right there in the list)
        if (SaveController.isDamaged(slots[pick])) {
            int del = JOptionPane.showConfirmDialog(root,
                    "The file in " + slots[pick] + " is damaged and cannot be loaded.\n"
                            + "Delete it? (You can also load the autosave instead.)",
                    "Damaged save", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (del == JOptionPane.YES_OPTION)
                showPopUp(SwingUtilities.getWindowAncestor(root), SaveController.deleteSlot(slots[pick]));
            return;
        }

        int sure = JOptionPane.showConfirmDialog(root,
                "Loading throws away the current unsaved progress. Continue?",
                "Load Game", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (sure != JOptionPane.YES_OPTION) return;

        String message = SaveController.loadGame(slots[pick]);
        if (message.startsWith("Loaded")) {
            closeActivePopup();
            // rebuild the whole game screen on top of the freshly loaded state
            Game.instance.changeScene(Menus.GAME_MENU);
        }
        JOptionPane.showMessageDialog(root, message);
    }

    public void updateWholeMap() {
        LandController.updateLandVisibility();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                graphicalMap[i][j].updateMap();
            }
        }
    }

    /**
     * Phase 3: applies a fresh multiplayer snapshot IN PLACE. Rebuilding all 225
     * tiles for every server update (the first approach) made each move feel
     * slow; instead every existing tile re-points at the new game copy, and only
     * the lightweight overlay (HUD labels, chips, buttons) is rebuilt. The camera
     * stays exactly where it was. from/to non-null = my own accepted move: a
     * short marker slide shows it instead of a silent teleport.
     */
    public void refreshFromSnapshot(Pair<Integer, Integer> from, Pair<Integer, Integer> to) {
        hideUnitPopup();
        // my own move: slide the marker over the OLD board first (the unit still
        // stands on its source hex there), and only then apply the new state -
        // otherwise the unit teleports and the marker trails after it, backwards
        if (from != null && to != null) {
            slideMoveMarker(from, to, this::applySnapshotNow);
        } else {
            applySnapshotNow();
        }
    }

    private void applySnapshotNow() {
        LandController.updateLandVisibility();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                graphicalMap[i][j].rebind();

        overlayLayer.removeAll();
        buildHud();
        buildBottomPanels();
        buildZoomControls();
        hudPanel.setBounds(0, 0, root.getWidth(), 46);
        updateHud();
        overlayLayer.revalidate();
        overlayLayer.repaint();
    }

    /** Quick straight-line slide of a marker between two hexes (~0.3s), purely visual. */
    private void slideMoveMarker(Pair<Integer, Integer> from, Pair<Integer, Integer> to, Runnable onDone) {
        double zoom = ZOOM_LEVELS[zoomIndex];
        Pair<Double, Double> a = graphicalMap[from.x][from.y].getCenterCoordinate();
        Pair<Double, Double> b = graphicalMap[to.x][to.y].getCenterCoordinate();
        JPanel marker = new JPanel();
        marker.setBackground(new Color(0xffe066));
        marker.setBorder(BorderFactory.createLineBorder(new Color(0x7a5c00), 2));
        int size = (int) (28 * zoom);
        mapPane.add(marker);
        mapPane.setComponentZOrder(marker, 0);
        final int steps = 8;
        final int[] step = {0};
        Timer timer = new Timer(35, e -> {
            double t = Math.min(1.0, step[0] / (double) steps);
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            marker.setBounds((int) (x * zoom - size / 2.0), (int) (y * zoom - size / 2.0), size, size);
            mapPane.repaint();
            if (step[0]++ >= steps) {
                ((Timer) e.getSource()).stop();
                mapPane.remove(marker);
                mapPane.repaint();
                if (onDone != null) onDone.run();
            }
        });
        timer.start();
    }

    public void updateHud() {
        Nation nation = GameController.getCurrentTurnUser().getNation();

        foodInfo.setText(resourceText(nation.getFood()));
        woodInfo.setText(resourceText(nation.getWood()));
        stoneInfo.setText(resourceText(nation.getStone()));
        ironInfo.setText(resourceText(nation.getIron()));

        // show red if either the balance is negative or we're losing this resource
        // this turn - before we only checked the rate so a negative balance with a
        // 0 rate looked green which was confusing
        foodInfo.setForeground(nation.getFood().getBalance() < 0 || nation.getFood().getNetRate() < 0 ? Color.RED : new Color(0x7ecf5a));
        woodInfo.setForeground(nation.getWood().getBalance() < 0 || nation.getWood().getNetRate() < 0 ? Color.RED : new Color(0xc8883a));
        stoneInfo.setForeground(nation.getStone().getBalance() < 0 || nation.getStone().getNetRate() < 0 ? Color.RED : new Color(0xb0b0b0));
        ironInfo.setForeground(nation.getIron().getBalance() < 0 || nation.getIron().getNetRate() < 0 ? Color.RED : new Color(0x6bafd6));

        unitCapInfo.setText(nation.getUnitCount() + "/" + nation.getUnitCap());
        unitCapInfo.setToolTipText(unitBreakdownText(nation));

        // phase 2: happiness value + current tier
        int happiness = nation.getHappiness();
        String tier = happiness >= 3 ? " GOLDEN AGE" : happiness <= -5 ? " RIOT!" : happiness <= -3 ? " STRIKE!" : "";
        happinessInfo.setText((happiness > 0 ? "+" : "") + happiness + tier);
        happinessInfo.setForeground(happiness >= 3 ? new Color(0xf0c060)
                : happiness <= -5 ? Color.RED
                : happiness <= -3 ? Color.ORANGE : new Color(0x7ecf5a));
        happinessInfo.setToolTipText("<html>-1 per Town, -1 at unit cap, -1 at military cap ("
                + nation.getMilitaryUnitCount() + "/" + nation.getMilitaryUnitCap()
                + "), +2 per Monument, +1 with a MILITARY unit garrisoned in the Town Hall.<br>"
                + "&ge;+3: Golden Age (+10% production) | -3..-4: strike (each worker -1) | &le;-5: riot (also -1 AP for all units)</html>");

        boolean starving = nation.isInStarvation();
        starvationLabel.setVisible(starving);

        int atRiskBuildings = 0;
        StringBuilder atRiskDetail = new StringBuilder("<html>");
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Improvement imp = Game.instance.map[hex.x][hex.y].getImprovement();
            if (imp != null && imp.getMissedUpkeepTurns() >= 2) {
                atRiskBuildings++;
                atRiskDetail.append(imp.getImprovementType().name).append(" at (").append(hex.x).append(",").append(hex.y)
                        .append(") - missed ").append(imp.getMissedUpkeepTurns()).append("/3 Wood upkeep payments<br>");
            }
        }
        atRiskDetail.append("A building is destroyed (its workers evicted) once it misses 3 upkeep payments in a row. "
                + "Each building needs 1 Wood/turn while staffed - station fewer Workers or grow your Wood income to save it.</html>");
        // keep this short - a long warning used to push the HUD buttons off-screen
        buildingWarningLabel.setText(atRiskBuildings + " building(s) at risk - hover!");
        buildingWarningLabel.setToolTipText(atRiskBuildings > 0 ? atRiskDetail.toString() : null);
        buildingWarningLabel.setVisible(atRiskBuildings > 0);

        turn.setText("Turn " + Game.instance.getTurn());
        yearLabel.setText("AD " + Game.instance.getTurn() * 50);

        // phase 2: current season, colored, with its effect as tooltip
        // the effect is also written next to the name because Spring/Summer were
        // hard to tell apart when the only difference was the label color
        Season season = Season.forTurn(Game.instance.getTurn());
        String seasonEffect = "";
        switch (season) {
            case SPRING: seasonLabel.setForeground(new Color(0x54d654)); seasonEffect = " (farms +1)"; break;
            case SUMMER: seasonLabel.setForeground(new Color(0xffd21f)); break;
            case AUTUMN: seasonLabel.setForeground(new Color(0xe1731f)); break;
            case WINTER: seasonLabel.setForeground(new Color(0xbfe3ff)); seasonEffect = " (farms -1, moves +1 AP)"; break;
        }
        seasonLabel.setText(season.name + seasonEffect);
        seasonLabel.setToolTipText("Seasons last 10 turns each. " + season.name + ": " + season.effectText());

        updateProductionPanel(nation);
        updateResearchPanel(nation);
    }

    /**
     * Surfaces the next unresearched, available technology so the player doesn't have
     * to open the Town Hall panel just to see whether anything new is researchable.
     * (Research itself goes through the Town Hall production queue and takes N turns;
     * in-progress research shows up in the production panel.)
     */
    private void updateResearchPanel(Nation nation) {
        TechnologyType next = null;
        for (TechnologyType tech : TechnologyType.values()) {
            if (nation.hasTechnology(tech)) continue;
            if (!tech.isAvailable(nation.getTechnologies())) continue;
            next = tech;
            break;
        }
        if (next == null) {
            boolean anyLocked = false;
            for (TechnologyType tech : TechnologyType.values()) {
                if (!nation.hasTechnology(tech)) { anyLocked = true; break; }
            }
            researchStatusLabel.setText(anyLocked ? "None available" : "All researched");
            researchStatusLabel.setForeground(Color.WHITE);
        } else {
            boolean affordable = nation.canAfford(0, next.woodCost, next.stoneCost, next.ironCost);
            researchStatusLabel.setText(next.name + (affordable ? " (ready)" : " (need resources)"));
            researchStatusLabel.setForeground(affordable ? new Color(0x7ecf5a) : new Color(0xaaaaaa));
        }
    }

    private String resourceText(Currency c) {
        return String.format("%d/%d | %+d", c.getBalance(), c.getCapacity(), c.getNetRate());
    }

    /** Unit-cap breakdown by type, shown as a tooltip on the HUD's Units label. */
    private String unitBreakdownText(Nation nation) {
        java.util.Map<CivilizedUnitType, Integer> counts = new java.util.EnumMap<>(CivilizedUnitType.class);
        for (Unit u : nation.getUnits()) {
            if (u instanceof CivilizedUnit) {
                CivilizedUnitType type = ((CivilizedUnit) u).getCivilizedUnitType();
                counts.merge(type, 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("<html>");
        for (CivilizedUnitType type : CivilizedUnitType.values()) {
            sb.append(type.name).append(": ").append(counts.getOrDefault(type, 0)).append("<br>");
        }
        sb.append("Military: ").append(nation.getMilitaryUnitCount()).append("/")
                .append(nation.getMilitaryUnitCap()).append(" (5 per TH level)<br>");
        sb.append("</html>");
        return sb.toString();
    }

    private void updateProductionPanel(Nation nation) {
        TownHall th = nation.getTownHall();
        if (th != null && th.hasQueuedProduction()) {
            ProductionOrder current = th.getCurrentProduction();
            int turnsLeft = current.getTurnsLeft();
            int queued = th.getProductionQueue().size();
            productionUnitName.setText(current.getDisplayName() + (queued > 1 ? " (+" + (queued - 1) + " queued)" : ""));
            productionTurnsLeft.setText(turnsLeft + " turn" + (turnsLeft == 1 ? "" : "s") + " left");
            double progress = 1.0 - ((double) turnsLeft / current.getTotalTurns());
            productionProgress.setValue((int) (Math.max(0, Math.min(1, progress)) * 100));
        } else {
            productionUnitName.setText("-");
            productionTurnsLeft.setText("Queue empty");
            productionProgress.setValue(0);
        }
    }

    public JLabel exCreator() {
        JLabel ex = new JLabel(scaledIcon(CommonIcons.EX.icon, 20, 20));
        return ex;
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    public void unitMove(JLabel unitMovement, Unit unit, int selection) {
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                graphicalMap[i][j].setMoveClickHandler((x, y) -> handleMoveClick(x, y, selection, unitMovement, unit));
            }
        }
    }

    private void handleMoveClick(int x, int y, int selection, JLabel unitMovement, Unit unit) {
        // phase 3: in multiplayer the move is a REQUEST - the server walks the unit
        // on the real map and answers with fresh snapshots for everyone
        if (MultiplayerSession.isActive()) {
            clearMoveHandlers();
            MultiplayerSession.get().sendMove(unit.getLocation(), x, y);
            return;
        }

        String message = UnitController.unitSetPath(x, y, selection);
        boolean accepted = UnitController.hasMoreSteps(unit);
        showPopUp(SwingUtilities.getWindowAncestor(root), message);
        clearMoveHandlers();
        if (accepted) {
            animateUnitAlongPath(unit, unitMovement);
        } else {
            unitMovement.setText("AP: " + unit.getCurrentAp() + "/" + unit.getMaxAp());
        }
    }

    private void clearMoveHandlers() {
        for (int k = 0; k < Consts.MAP_SIZE.amount.x; k++) {
            for (int l = 0; l < Consts.MAP_SIZE.amount.y; l++) {
                graphicalMap[k][l].setMoveClickHandler(null);
            }
        }
    }

    /**
     * Walks the unit one hex at a time (instead of teleporting straight to the destination),
     * sliding a marker between hex centers so movement is visibly animated on the map.
     */
    private JComponent movementMarker;

    private void animateUnitAlongPath(Unit unit, JLabel unitMovement) {
        JPanel marker = new JPanel();
        marker.setBackground(new Color(0xffe066));
        marker.setBorder(BorderFactory.createLineBorder(new Color(0x7a5c00), 2));
        // tile positions are stored unscaled and multiplied by the current zoom level
        // when drawn (see LandGraphical.applyBounds) - the marker has to do the same
        // scaling or it ends up in the wrong spot on screen whenever zoom != 1.0
        double zoom = ZOOM_LEVELS[zoomIndex];
        int size = (int) (36 * zoom);
        Pair<Double, Double> startCenter = graphicalMap[unit.getLocation().x][unit.getLocation().y].getCenterCoordinate();
        marker.setBounds((int) (startCenter.x * zoom - size / 2.0), (int) (startCenter.y * zoom - size / 2.0), size, size);
        mapPane.add(marker);
        mapPane.setComponentZOrder(marker, 0);
        movementMarker = marker;

        runMovementStep(unit, unitMovement, marker);
    }

    private void runMovementStep(Unit unit, JLabel unitMovement, JComponent marker) {
        if (!UnitController.hasMoreSteps(unit)) {
            mapPane.remove(marker);
            mapPane.repaint();
            updateWholeMap();
            // moving on/off the Town Hall hex changes the garrison happiness bonus
            updateHud();
            unitMovement.setText("AP: " + unit.getCurrentAp() + "/" + unit.getMaxAp());
            // Reopen the unit popup so the Move button's enabled-state reflects the AP
            // actually spent, instead of staying stuck at what it was when the popup
            // was first built (see qa-pass-2026-07-06 finding: bug #3).
            if (activePopup != null && GameController.getSelectedCivilizedUnit() == unit) {
                showSelectedCivilizedUnitInfo();
            }
            return;
        }

        Pair<Integer, Integer> from = unit.getLocation();
        UnitController.unitGoForward(unit);
        Pair<Integer, Integer> to = unit.getLocation();

        graphicalMap[from.x][from.y].updateMap();
        graphicalMap[to.x][to.y].updateMap();

        if (UnitController.lastStepRetrievedRuin) {
            UnitController.lastStepRetrievedRuin = false;
            showPopUp(SwingUtilities.getWindowAncestor(root), "Ruin retrieved.");
        }

        if (UnitController.lastStepBlocked) {
            UnitController.lastStepBlocked = false;
            Pair<Integer, Integer> blockedHex = UnitController.lastBlockedHex;
            UnitController.lastBlockedHex = null;
            mapPane.remove(marker);
            mapPane.repaint();
            updateWholeMap();
            updateHud();
            unitMovement.setText("AP: " + unit.getCurrentAp() + "/" + unit.getMaxAp());
            String blockerName = "another unit";
            if (blockedHex != null) {
                CivilizedUnit blocker = Game.instance.map[blockedHex.x][blockedHex.y].getCivilizedUnit();
                if (blocker != null) blockerName = blocker.getCivilizedUnitType().name;
            }
            String stoppedAt = "(" + unit.getLocation().x + "," + unit.getLocation().y + ")";
            String blockedAt = blockedHex == null ? "" : " at (" + blockedHex.x + "," + blockedHex.y + ")";
            showPopUp(SwingUtilities.getWindowAncestor(root),
                    "Stopped at " + stoppedAt + ": the next tile" + blockedAt + " is occupied by a " + blockerName
                            + ". The unit did not disappear - select it again and re-issue Move once the tile is clear.");
            if (activePopup != null && GameController.getSelectedCivilizedUnit() == unit) {
                showSelectedCivilizedUnitInfo();
            }
            return;
        }

        double zoom = ZOOM_LEVELS[zoomIndex];
        Pair<Double, Double> fromCenter = graphicalMap[from.x][from.y].getCenterCoordinate();
        Pair<Double, Double> toCenter = graphicalMap[to.x][to.y].getCenterCoordinate();

        int size = marker.getWidth();
        int steps = 18; // 180ms / ~10ms per tick
        Timer timer = new Timer(10, null);
        int[] tick = {0};
        timer.addActionListener(e -> {
            tick[0]++;
            double t = tick[0] / (double) steps;
            double x = (fromCenter.x + (toCenter.x - fromCenter.x) * t) * zoom;
            double y = (fromCenter.y + (toCenter.y - fromCenter.y) * t) * zoom;
            marker.setBounds((int) (x - size / 2.0), (int) (y - size / 2.0), size, size);
            if (tick[0] >= steps) {
                timer.stop();
                unitMovement.setText("AP: " + unit.getCurrentAp() + "/" + unit.getMaxAp());
                runMovementStep(unit, unitMovement, marker);
            }
        });
        timer.start();
    }

    public JScrollPane buildImprovement() {
        JPanel improvements = new JPanel();
        improvements.setLayout(new BoxLayout(improvements, BoxLayout.Y_AXIS));
        improvements.setBackground(new Color(0x212121));

        for (ImprovementType improvementType : ImprovementType.values()) {
            // trading posts are neutral pre-placed structures, never player-buildable
            if (improvementType == ImprovementType.TRADING_POST) continue;
            JLabel nameLabel = new JLabel(improvementType.name);
            String costTip = "Wood:" + improvementType.buildCostWood +
                    " Stone:" + improvementType.buildCostStone +
                    " Iron:" + improvementType.buildCostIron;

            // Doc: disable options the player can't currently use, rather than erroring after the click.
            String blockedReason = WorkerController.checkCanBuild(improvementType);
            if (blockedReason == null) {
                nameLabel.setForeground(Color.WHITE);
                nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                nameLabel.setToolTipText(costTip);
                nameLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        CivilizedUnit builder = GameController.getSelectedCivilizedUnit();
                        String message = WorkerController.builderBuildImprovement(improvementType.name);
                        showPopUp(SwingUtilities.getWindowAncestor(root), message);
                        updateWholeMap();
                        updateHud();
                        // Rebuild the popup (and this submenu) so a consumed Builder closes
                        // the popup instead of leaving stale, now-invalid build options
                        // clickable, and so a surviving Builder's charge count / AP and each
                        // improvement's eligibility reflect what just happened.
                        if (builder != null && GameController.getSelectedCivilizedUnit() == builder) {
                            showSelectedCivilizedUnitInfo();
                        } else {
                            closeActivePopup();
                        }
                    }
                });
            } else {
                nameLabel.setForeground(Color.GRAY);
                nameLabel.setToolTipText(blockedReason);
            }
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            improvements.add(nameLabel);
            improvements.add(Box.createVerticalStrut(4));
        }

        JScrollPane improvementsScrollPane = new JScrollPane(improvements);
        improvementsScrollPane.setPreferredSize(new Dimension(100, 230));
        improvementsScrollPane.setBackground(new Color(0x212121));
        return improvementsScrollPane;
    }

    public void nextTurn() {
        Nation currentNation = GameController.getCurrentTurnUser().getNation();
        boolean hasIdleUnits = false;
        for (Unit u : currentNation.getUnits()) {
            if (u.getUnitStatus() == UnitStatus.AWAKE && u.getCurrentAp() > 0) {
                if (u instanceof CivilizedUnit && ((CivilizedUnit) u).isStationed()) continue;
                hasIdleUnits = true;
                break;
            }
        }

        if (hasIdleUnits) {
            int result = JOptionPane.showConfirmDialog(root,
                    "Some units still have action points left. End turn anyway?",
                    "Idle Units", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;
        }

        // phase 3: in multiplayer the SERVER ends the turn, never this client
        if (MultiplayerSession.isActive()) {
            if (!MultiplayerSession.isMyTurn()) {
                showPopUp(SwingUtilities.getWindowAncestor(root),
                        "It is " + MultiplayerSession.currentPlayerName() + "'s turn, not yours.");
                return;
            }
            MultiplayerSession.get().sendEndTurn();
            return;
        }

        String message = GameController.endTurn();

        // phase 2: show everything that happened during turn processing as one report
        if (GameController.turnNotifications.isEmpty()) {
            showPopUp(SwingUtilities.getWindowAncestor(root), message);
        } else {
            StringBuilder report = new StringBuilder("<html><b>" + message + "</b>");
            for (String n : GameController.turnNotifications) report.append("<br>• ").append(n);
            report.append("</html>");
            showPopUp(SwingUtilities.getWindowAncestor(root), report.toString());
        }

        hideUnitPopup();
        updateWholeMap();
        updateHud();

        // phase 2 disasters: if a quake hit during turn processing, shake the map
        // and flash the damaged area (an alert alone is not accepted by the spec)
        if (DisasterController.lastQuakeArea != null) {
            playEarthquakeAnimation(DisasterController.lastQuakeArea);
            DisasterController.lastQuakeArea = null;
            DisasterController.lastQuakeCenter = null;
        }

        // phase 2 combat: enemies fought back during turn processing - show every
        // battle's dice (a battle must never resolve silently, P2-15)
        showPendingEnemyBattles();
    }

    // ── Phase 2 combat: dice battle dialog (P2-15) ────────────────────────────
    // shows every die BOTH graphically (big dice-face glyphs) and numerically, plus
    // one line per compared pair - per spec a battle may never resolve silently

    private void showBattleDialog(CombatController.BattleReport report) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0x1e1e1e));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel(report.title);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(new Color(0xf0c060));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(10));

        if (report.attackerDice.length > 0)
            panel.add(diceRow("Attacker rolls:", report.attackerDice, new Color(0xe0685e)));
        if (report.defenderDice.length > 0)
            panel.add(diceRow("Defender rolls:", report.defenderDice, new Color(0x6ea8dc)));

        panel.add(Box.createVerticalStrut(8));
        for (String line : report.lines) {
            JLabel l = new JLabel(line);
            l.setForeground(Color.WHITE);
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(l);
        }

        if (!report.summary.isEmpty()) {
            panel.add(Box.createVerticalStrut(8));
            JLabel summary = new JLabel(report.summary);
            summary.setFont(new Font("SansSerif", Font.BOLD, 13));
            summary.setForeground(new Color(0xf0c060));
            summary.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(summary);
        }
        JOptionPane.showMessageDialog(root, panel, "Battle!", JOptionPane.PLAIN_MESSAGE);
    }

    /** One row of dice: caption, then each die as a big Unicode dice face + its number. */
    private JPanel diceRow(String caption, int[] dice, Color color) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cap = new JLabel(caption);
        cap.setForeground(color);
        cap.setFont(new Font("SansSerif", Font.BOLD, 13));
        cap.setPreferredSize(new Dimension(110, 44));
        row.add(cap);
        for (int die : dice) {
            // U+2680 is the one-pip die face, so value N is 0x2680 + N - 1
            JLabel face = new JLabel(String.valueOf((char) (0x2680 + die - 1)));
            face.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 42));
            face.setForeground(color);
            row.add(face);
            JLabel number = new JLabel(String.valueOf(die));
            number.setFont(new Font("SansSerif", Font.BOLD, 18));
            number.setForeground(Color.WHITE);
            row.add(number);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        return row;
    }

    // ── Phase 3 multiplayer: attack / diplomacy / trade UI ────────────────────

    /** A war/battle report pushed by the server - shown with the same dice dialog. */
    public void showNetworkBattle(sut.civilization.Network.Messages.BattleReportMessage message) {
        CombatController.BattleReport report = new CombatController.BattleReport();
        report.title = message.getTitle();
        report.attackerDice = message.getAttackerDice() == null ? new int[0] : message.getAttackerDice();
        report.defenderDice = message.getDefenderDice() == null ? new int[0] : message.getDefenderDice();
        if (message.getLines() != null) report.lines.addAll(message.getLines());
        report.summary = message.getSummary() == null ? "" : message.getSummary();
        showBattleDialog(report);
    }

    /** Everything this unit could attack right now, judged on the render copy (server re-checks anyway). */
    private java.util.ArrayList<Pair<Integer, Integer>> multiplayerTargetsInRange(CivilizedUnit unit) {
        java.util.ArrayList<Pair<Integer, Integer>> targets = new java.util.ArrayList<>();
        Nation mine = Game.instance.getLoggedInUser().getNation();
        for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(unit.getLocation(), unit.getCivilizedUnitType().attackRange)) {
            if (hex.equals(unit.getLocation())) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            if (tile.getVisibility() != 2) continue; // can't attack into fog
            if (tile.getEnemy() != null) { targets.add(hex); continue; }
            CivilizedUnit other = tile.getCivilizedUnit();
            if (other != null && other.getOwnerNation() != mine) { targets.add(hex); continue; }
            for (User u : Game.instance.getPlayersInGame()) {
                if (u.getNation() == mine) continue;
                TownHall th = u.getNation().getTownHall();
                if (th != null && th.getMainLand() == tile) { targets.add(hex); break; }
            }
        }
        return targets;
    }

    private String describeMultiplayerTarget(Pair<Integer, Integer> hex) {
        Land tile = Game.instance.map[hex.x][hex.y];
        if (tile.getEnemy() != null)
            return tile.getEnemy().getType().name + " at (" + hex.x + "," + hex.y + ")";
        if (tile.getCivilizedUnit() != null)
            return tile.getCivilizedUnit().getCivilizedUnitType().name + " at (" + hex.x + "," + hex.y + ")";
        return "Town Hall at (" + hex.x + "," + hex.y + ")";
    }

    /** MP version of the Attack button: pick a target, send an AttackRequest, let the server roll. */
    private void wireMultiplayerAttack(JButton actionButton, CivilizedUnit unit) {
        String blocked = null;
        if (!unit.getCivilizedUnitType().isMilitary) blocked = "Only military units can attack!";
        else if (!unit.hasAp()) blocked = "No AP left!";
        else if (multiplayerTargetsInRange(unit).isEmpty()) blocked = "No target in range!";
        actionButton.setEnabled(blocked == null);
        if (blocked != null) {
            actionButton.setToolTipText(blocked);
            return;
        }
        actionButton.addActionListener(e -> {
            java.util.ArrayList<Pair<Integer, Integer>> targets = multiplayerTargetsInRange(unit);
            if (targets.isEmpty()) return;
            Pair<Integer, Integer> picked = targets.get(0);
            if (targets.size() > 1) {
                String[] options = new String[targets.size()];
                for (int i = 0; i < targets.size(); i++) options[i] = describeMultiplayerTarget(targets.get(i));
                int pick = JOptionPane.showOptionDialog(root, "Attack which target?", "Attack",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (pick < 0) return;
                picked = targets.get(pick);
            }
            MultiplayerSession.get().sendAttack(unit.getLocation(), picked.x, picked.y);
        });
    }

    private Color relationColor(sut.civilization.Model.ModulEnums.Relation relation) {
        switch (relation) {
            case ENEMY: return new Color(0xe0685e);
            case ALLY: return new Color(0x9acd68);
            default: return new Color(0xbbbbbb);
        }
    }

    /** Diplomacy panel (P3-38..41): current relation per player, color-coded, with the possible actions. */
    private void showDiplomacyDialog() {
        String myName = MultiplayerSession.get().getMyName();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(root), "Diplomacy");
        for (User user : Game.instance.getPlayersInGame()) {
            if (user.getUsername().equals(myName)) continue;
            String them = user.getUsername();
            sut.civilization.Model.ModulEnums.Relation relation = Game.instance.relationBetween(myName, them);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JLabel name = new JLabel(them + " - " + relation.label
                    + (Game.instance.isEliminated(them) ? " (eliminated)" : ""));
            name.setForeground(relationColor(relation));
            name.setFont(new Font("SansSerif", Font.BOLD, 13));
            row.add(name);

            if (!Game.instance.isEliminated(them)) {
                if (relation == sut.civilization.Model.ModulEnums.Relation.NEUTRAL) {
                    JButton war = new JButton("Declare War");
                    war.addActionListener(e -> { MultiplayerSession.get().sendDeclareWar(them); dialog.dispose(); });
                    row.add(war);
                    JButton ally = new JButton("Offer Alliance");
                    ally.addActionListener(e -> { MultiplayerSession.get().sendOfferAlliance(them); dialog.dispose(); });
                    row.add(ally);
                } else if (relation == sut.civilization.Model.ModulEnums.Relation.ALLY) {
                    JButton breakIt = new JButton("Break Alliance");
                    breakIt.addActionListener(e -> { MultiplayerSession.get().sendBreakAlliance(them); dialog.dispose(); });
                    row.add(breakIt);
                }
                // an offer FROM them waiting for MY answer
                if (myName.equals(Game.instance.getAllianceOffers().get(them))) {
                    JButton accept = new JButton("Accept Alliance");
                    accept.addActionListener(e -> { MultiplayerSession.get().sendAllianceReply(them, true); dialog.dispose(); });
                    row.add(accept);
                    JButton decline = new JButton("Decline");
                    decline.addActionListener(e -> { MultiplayerSession.get().sendAllianceReply(them, false); dialog.dispose(); });
                    row.add(decline);
                }
                // my own offer still waiting on them
                if (them.equals(Game.instance.getAllianceOffers().get(myName))) {
                    JLabel waiting = new JLabel("(alliance offer sent - waiting)");
                    waiting.setForeground(new Color(0x888888));
                    row.add(waiting);
                }
            }
            panel.add(row);
        }

        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(panel));
        dialog.setSize(430, 220);
        dialog.setLocationRelativeTo(root);
        dialog.setVisible(true);
    }

    /**
     * Demo shortcuts for showing rare scenarios fast (P3-58). Every action is a
     * server request: host-only, applied to the authoritative game, announced
     * to all players in the next broadcast - never a silent local edit.
     */
    private void showMultiplayerDemoDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(root), "Demo Actions");
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel note = new JLabel("<html>Host-only, executed by the SERVER and announced to every player.<br>"
                + "For demoing scenarios fast (war, elimination, trades).</html>");
        note.setForeground(new Color(0x888888));
        panel.add(note);
        panel.add(Box.createVerticalStrut(8));

        String[][] actions = {
                {sut.civilization.Network.Messages.DebugActionRequest.RESOURCES, "Fill my storage with resources"},
                {sut.civilization.Network.Messages.DebugActionRequest.TELEPORT_TO_ENEMY, "Teleport my soldier next to the enemy Town Hall"},
                {sut.civilization.Network.Messages.DebugActionRequest.WEAKEN_ENEMY_TH, "Set the enemy Town Hall HP to 15"},
                {sut.civilization.Network.Messages.DebugActionRequest.REFRESH_AP, "Refill my units' AP"},
                {sut.civilization.Network.Messages.DebugActionRequest.CLEAR_HOSTILES, "Remove all bears / barbarians / camps"},
        };
        for (String[] action : actions) {
            JButton b = new JButton(action[1]);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.addActionListener(e -> { MultiplayerSession.get().sendDebugAction(action[0]); dialog.dispose(); });
            panel.add(b);
            panel.add(Box.createVerticalStrut(4));
        }

        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(panel));
        dialog.setSize(420, 320);
        dialog.setLocationRelativeTo(root);
        dialog.setVisible(true);
    }

    /** MP Town Hall: train units through the server (the spec's build-then-fight flow). */
    private void showMultiplayerTownHallDialog() {
        Nation mine = Game.instance.getLoggedInUser().getNation();
        TownHall th = mine.getTownHall();
        if (th == null) {
            showPopUp(SwingUtilities.getWindowAncestor(root), "You have no Town Hall anymore.");
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(root), "Town Hall - Train Units");
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel status = new JLabel(th.getLevelName() + " (level " + th.getLevel() + ")  HP " + th.getHp() + "/" + th.getMaxHp());
        status.setFont(new Font("SansSerif", Font.BOLD, 13));
        panel.add(status);
        JLabel queue = new JLabel(th.hasQueuedProduction()
                ? "In production: " + th.getCurrentProduction().getDisplayName()
                + " (" + th.getCurrentProduction().getTurnsLeft() + " turns left)"
                : "Production queue is empty.");
        queue.setForeground(new Color(0x666666));
        panel.add(queue);
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JSeparator());

        for (CivilizedUnitType type : CivilizedUnitType.values()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            StringBuilder info = new StringBuilder(type.name + "  -  " + type.productionCostFood + " food");
            if (type.productionCostWood > 0) info.append(" + ").append(type.productionCostWood).append(" wood");
            info.append(", ").append(type.turnsToTrain).append(" turn").append(type.turnsToTrain == 1 ? "" : "s");
            if (type.requiredTownHallLevel > 1) info.append(", needs TH level ").append(type.requiredTownHallLevel);
            if (type.requiresMilitaryStable) info.append(" + Military Stable");
            row.add(new JLabel(info.toString()));
            JButton train = new JButton("Train");
            // the server re-checks everything and answers with the exact reason if not
            train.addActionListener(e -> { MultiplayerSession.get().sendTrainUnit(type.name); dialog.dispose(); });
            row.add(train);
            panel.add(row);
        }
        panel.add(new JSeparator());
        JPanel upgradeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        upgradeRow.add(new JLabel(th.getLevel() >= 3 ? "Town Hall is at max level."
                : th.getLevel() == 1 ? "Upgrade to Settlement (lvl 2)  -  50 wood + 50 stone, 3 turns"
                : "Upgrade to Capital (lvl 3)  -  100 stone + 50 iron, 5 turns"));
        if (th.getLevel() < 3) {
            JButton upgrade = new JButton("Upgrade");
            upgrade.addActionListener(e -> { MultiplayerSession.get().sendUpgradeTownHall(); dialog.dispose(); });
            upgradeRow.add(upgrade);
        }
        panel.add(upgradeRow);

        panel.add(new JSeparator());
        JLabel researchHeader = new JLabel("Research (server-validated, one production at a time):");
        researchHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(researchHeader);
        Nation myNation = Game.instance.getLoggedInUser().getNation();
        for (TechnologyType tech : TechnologyType.values()) {
            if (myNation.hasTechnology(tech)) continue; // done - nothing to click
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            StringBuilder info = new StringBuilder(tech.name + "  -  ");
            if (tech.woodCost > 0) info.append(tech.woodCost).append(" wood ");
            if (tech.stoneCost > 0) info.append(tech.stoneCost).append(" stone ");
            if (tech.ironCost > 0) info.append(tech.ironCost).append(" iron ");
            info.append(", ").append(tech.turnsToResearch).append(" turns");
            if (tech.requiredTownHallLevel > 1) info.append(", needs TH level ").append(tech.requiredTownHallLevel);
            row.add(new JLabel(info.toString()));
            JButton research = new JButton("Research");
            research.addActionListener(e -> { MultiplayerSession.get().sendResearch(tech.name); dialog.dispose(); });
            row.add(research);
            panel.add(row);
        }

        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(panel));
        dialog.setSize(520, 560);
        dialog.setLocationRelativeTo(root);
        dialog.setVisible(true);
    }

    /** Player-to-player trade (P3-35..37): compose an offer + answer the inbox. */
    private void showMultiplayerTradeDialog() {
        String myName = MultiplayerSession.get().getMyName();
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(root), "Player Trade");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── new offer form ──
        java.util.ArrayList<String> others = new java.util.ArrayList<>();
        for (User user : Game.instance.getPlayersInGame())
            if (!user.getUsername().equals(myName) && !Game.instance.isEliminated(user.getUsername()))
                others.add(user.getUsername());
        JComboBox<String> targetCombo = new JComboBox<>(others.toArray(new String[0]));

        String[] resources = {"Food", "Wood", "Stone", "Iron"};
        JSpinner[] give = new JSpinner[4];
        JSpinner[] want = new JSpinner[4];
        JPanel form = new JPanel(new GridLayout(3, 5, 6, 2));
        form.add(new JLabel(""));
        for (String r : resources) form.add(new JLabel(r));
        form.add(new JLabel("You give:"));
        for (int i = 0; i < 4; i++) { give[i] = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1)); form.add(give[i]); }
        form.add(new JLabel("You want:"));
        for (int i = 0; i < 4; i++) { want[i] = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1)); form.add(want[i]); }
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JButton send = new JButton("Send Offer");
        send.addActionListener(e -> {
            if (targetCombo.getSelectedItem() == null) return;
            int[] g = new int[4], w = new int[4];
            for (int i = 0; i < 4; i++) { g[i] = (int) give[i].getValue(); w[i] = (int) want[i].getValue(); }
            MultiplayerSession.get().sendTradeOffer((String) targetCombo.getSelectedItem(), g, w);
            dialog.dispose();
        });

        JPanel sendRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sendRow.add(new JLabel("To:"));
        sendRow.add(targetCombo);
        sendRow.add(send);

        JLabel formHeader = new JLabel("New offer (the server locks what you give until it is answered):");
        formHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(formHeader);
        panel.add(form);
        panel.add(sendRow);
        panel.add(new JSeparator());

        // ── inbox / outbox ──
        JLabel inboxHeader = new JLabel("Trade inbox:");
        inboxHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(inboxHeader);
        boolean any = false;
        for (sut.civilization.Model.Classes.TradeOffer offer : Game.instance.getTradeOffers()) {
            if (!offer.isPending()) continue;
            if (!offer.to.equals(myName) && !offer.from.equals(myName)) continue;
            any = true;
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            if (offer.to.equals(myName)) {
                row.add(new JLabel(offer.from + " gives " + offer.describeGive() + " for your " + offer.describeWant()));
                JButton accept = new JButton("Accept");
                accept.addActionListener(e -> { MultiplayerSession.get().sendTradeReply(offer.id, true); dialog.dispose(); });
                JButton reject = new JButton("Reject");
                reject.addActionListener(e -> { MultiplayerSession.get().sendTradeReply(offer.id, false); dialog.dispose(); });
                row.add(accept);
                row.add(reject);
            } else {
                row.add(new JLabel("To " + offer.to + ": your " + offer.describeGive() + " for " + offer.describeWant() + " (waiting)"));
                JButton cancel = new JButton("Cancel");
                cancel.addActionListener(e -> { MultiplayerSession.get().sendTradeReply(offer.id, false); dialog.dispose(); });
                row.add(cancel);
            }
            panel.add(row);
        }
        if (!any) {
            JLabel none = new JLabel("No waiting offers.");
            none.setForeground(new Color(0x888888));
            panel.add(none);
        }

        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(panel));
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(root);
        dialog.setVisible(true);
    }

    /** Shows the dice of every enemy attack that happened during turn processing. */
    private void showPendingEnemyBattles() {
        if (CombatController.lastEnemyBattles.isEmpty()) return;
        for (CombatController.BattleReport report : new java.util.ArrayList<>(CombatController.lastEnemyBattles))
            showBattleDialog(report);
        CombatController.lastEnemyBattles.clear();
    }

    private Timer quakeShakeTimer;
    private int quakeShakeBase;

    /** Screen shake + orange flash over the affected hexes for ~1.2s. */
    private void playEarthquakeAnimation(java.util.List<Pair<Integer, Integer>> area) {
        JScrollBar hbar = mapScrollPane.getHorizontalScrollBar();
        // a new quake while the previous one is still shaking: finish the old one
        // first, otherwise the two flash areas merge into one giant blob
        if (quakeShakeTimer != null && quakeShakeTimer.isRunning()) {
            quakeShakeTimer.stop();
            hbar.setValue(quakeShakeBase);
            LandGraphical.quakeFlashTiles.clear();
            updateWholeMap();
        }

        LandGraphical.quakeFlashTiles.addAll(area);
        for (Pair<Integer, Integer> hex : area) graphicalMap[hex.x][hex.y].repaint();

        int base = hbar.getValue();
        quakeShakeBase = base;
        int[] tick = {0};
        Timer shake = new Timer(40, null);
        shake.addActionListener(e -> {
            tick[0]++;
            if (tick[0] >= 30) {
                shake.stop();
                hbar.setValue(base);
                LandGraphical.quakeFlashTiles.clear();
                updateWholeMap();
            } else {
                hbar.setValue(base + (tick[0] % 2 == 0 ? 10 : -10));
            }
        });
        quakeShakeTimer = shake;
        shake.start();
    }

    public void hideUnitPopup() {
        GameController.setSelectedCivilizedUnit(null);
        closeActivePopup();
    }

    // ── Phase 2: weather animations (winter snowfall, autumn rain) ────────────
    // A transparent layer above the map that animates simple falling particles for
    // the whole season and stops when the season changes. contains() always says
    // false so mouse clicks fall straight through to the map tiles underneath.
    private static class WeatherOverlay extends JPanel {
        private static class Particle { double x, y, speed, drift; int size; }

        private final java.util.ArrayList<Particle> particles = new java.util.ArrayList<>();
        private Season activeSeason = null;

        WeatherOverlay() {
            setOpaque(false);
            // ~25 fps is plenty for background weather
            new Timer(40, e -> tick()).start();
        }

        @Override
        public boolean contains(int x, int y) { return false; }

        private void tick() {
            Season season = Season.forTurn(Game.instance.getTurn());
            boolean active = season == Season.WINTER || season == Season.AUTUMN;
            if (!active) {
                if (!particles.isEmpty() || activeSeason != null) {
                    particles.clear();
                    activeSeason = null;
                    repaint();
                }
                return;
            }
            if (season != activeSeason) { particles.clear(); activeSeason = season; }

            // top up to the target count; new particles start above the top edge
            int target = season == Season.WINTER ? 90 : 130;
            while (particles.size() < target) {
                Particle p = new Particle();
                p.x = Math.random() * Math.max(1, getWidth());
                p.y = -Math.random() * Math.max(1, getHeight());
                if (season == Season.WINTER) {
                    p.speed = 1 + Math.random() * 1.5;      // slow snow
                    p.drift = Math.random() - 0.5;          // gentle sideways sway
                    p.size = 2 + (int) (Math.random() * 3);
                } else {
                    p.speed = 8 + Math.random() * 5;        // fast rain
                    p.drift = -2;                           // storm wind blows it sideways
                    p.size = 10 + (int) (Math.random() * 8);
                }
                particles.add(p);
            }
            for (Particle p : particles) {
                p.y += p.speed;
                p.x += p.drift;
                if (p.y > getHeight() || p.x < -20) {
                    p.y = -10;
                    p.x = Math.random() * Math.max(1, getWidth());
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (activeSeason == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (activeSeason == Season.WINTER) {
                g2.setColor(new Color(255, 255, 255, 200));
                for (Particle p : particles) g2.fillOval((int) p.x, (int) p.y, p.size, p.size);
            } else { // autumn rain, drawn as short wind-slanted streaks
                g2.setColor(new Color(160, 200, 255, 160));
                g2.setStroke(new BasicStroke(1.6f));
                for (Particle p : particles) g2.drawLine((int) p.x, (int) p.y, (int) (p.x - 4), (int) (p.y + p.size));
            }
            g2.dispose();
        }
    }
}

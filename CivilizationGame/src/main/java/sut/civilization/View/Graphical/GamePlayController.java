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

    public JLabel foodInfo;
    public JLabel woodInfo;
    public JLabel stoneInfo;
    public JLabel ironInfo;
    public JLabel unitCapInfo;
    public JLabel starvationLabel;
    public JLabel buildingWarningLabel;
    public JLabel turn;
    public JLabel yearLabel;

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
        gamePlayController = this;

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

        mapScrollPane = new JScrollPane(mapPane);
        mapScrollPane.getVerticalScrollBar().setUnitIncrement(30);
        mapScrollPane.getHorizontalScrollBar().setUnitIncrement(30);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(mapScrollPane, BorderLayout.CENTER);
        centerWrapper.setBounds(0, 0, 1366, 768);
        layeredRoot.add(centerWrapper, JLayeredPane.DEFAULT_LAYER);

        overlayLayer = new JPanel(null);
        overlayLayer.setOpaque(false);
        overlayLayer.setBounds(0, 0, 1366, 768);
        layeredRoot.add(overlayLayer, JLayeredPane.PALETTE_LAYER);

        buildHud();
        buildBottomPanels();
        buildZoomControls();

        root.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layeredRoot.setBounds(0, 0, root.getWidth(), root.getHeight());
                centerWrapper.setBounds(0, 0, root.getWidth(), root.getHeight());
                overlayLayer.setBounds(0, 0, root.getWidth(), root.getHeight());
                layeredRoot.revalidate();
                layeredRoot.repaint();
            }
        });

        updateHud();

        // Center the initial view on the Town Hall's starting area. The scrollbars' real
        // extents aren't known until the viewport has been laid out, so defer past one
        // extra paint cycle rather than a single invokeLater (which can still race layout).
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            mapScrollPane.revalidate();
            JScrollBar hbar = mapScrollPane.getHorizontalScrollBar();
            JScrollBar vbar = mapScrollPane.getVerticalScrollBar();
            hbar.setValue((int) (hbar.getMaximum() * 0.45));
            vbar.setValue((int) (vbar.getMaximum() * 0.45));
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
        JPanel hud = new JPanel();
        hud.setBackground(new Color(0x22, 0x22, 0x22, 220));
        hud.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 5));
        hud.setBounds(0, 0, 1366, 40);

        foodInfo = hudLabel();
        woodInfo = hudLabel();
        stoneInfo = hudLabel();
        ironInfo = hudLabel();
        unitCapInfo = hudLabel();
        starvationLabel = hudLabel();
        starvationLabel.setText("STARVING!");
        starvationLabel.setForeground(Color.RED);
        starvationLabel.setVisible(false);
        buildingWarningLabel = hudLabel();
        buildingWarningLabel.setForeground(Color.ORANGE);
        buildingWarningLabel.setVisible(false);
        turn = hudLabel();
        yearLabel = hudLabel();

        hud.add(labeled("Food:", foodInfo));
        hud.add(labeled("Wood:", woodInfo));
        hud.add(labeled("Stone:", stoneInfo));
        hud.add(labeled("Iron:", ironInfo));
        hud.add(labeled("Units:", unitCapInfo));
        hud.add(turn);
        hud.add(yearLabel);
        hud.add(starvationLabel);
        hud.add(buildingWarningLabel);

        JButton townHallBtn = hudButton("Town Hall");
        JButton unitsBtn = hudButton("Units");
        JButton statsBtn = hudButton("Stats");
        JButton menuBtn = hudButton("Menu");

        townHallBtn.addActionListener(e -> showTownHallPanel());
        unitsBtn.addActionListener(e -> showUnits());
        statsBtn.addActionListener(e -> showDemographics());
        menuBtn.addActionListener(e -> showMenu());

        hud.add(townHallBtn);
        hud.add(unitsBtn);
        hud.add(statsBtn);
        hud.add(menuBtn);

        overlayLayer.add(hud);
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
    public void showTradePanel() { }
    public void showEconomics() { }

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
            JLabel units = new JLabel("Units: " + nation.getUnits().size() + "/" + nation.getUnitCap());
            JLabel territory = new JLabel("Territory: " + nation.getTerritory().size() + " hexes");
            JLabel food = new JLabel("Food: " + nation.getFood().getBalance());
            JLabel wood = new JLabel("Wood: " + nation.getWood().getBalance());
            JLabel stone = new JLabel("Stone: " + nation.getStone().getBalance());
            JLabel iron = new JLabel("Iron: " + nation.getIron().getBalance());
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

    private void showPopupPanel(JComponent content) {
        closeActivePopup();
        Window window = SwingUtilities.getWindowAncestor(root);

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
        activePopup.setVisible(true);
    }

    private void closeActivePopup() {
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
        JLabel unitHP = new JLabel("Health: " + civilizedUnit.getHp());
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

    private void handleCommonActions(UnitActions action, Unit unit, JLabel unitMovement, int selection, JButton actionButton) {
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
        content.setPreferredSize(new Dimension(480, 500));

        JLabel header = new JLabel("Town Hall - Upgrade Tree");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setForeground(new Color(0xf0c060));
        content.add(header);

        if (townHall == null) {
            JLabel noTh = new JLabel("No Town Hall placed yet.");
            noTh.setForeground(Color.WHITE);
            content.add(noTh);
        } else {
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
                boolean researchable = !done && available && affordable;

                JButton btn = new JButton(done ? "Done" : !available ? "Locked" : !affordable ? "Can't Afford" : "Research");
                btn.setEnabled(researchable);
                btn.setBackground(done ? new Color(0x2d6a2d) : researchable ? new Color(0x1a5276) : new Color(0x444444));
                btn.setForeground(Color.WHITE);
                if (!done && available && !affordable) {
                    btn.setToolTipText("Not enough resources: need " + costString(tech).trim());
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
                nameLabel.setForeground(Color.WHITE);

                JLabel costLabel = new JLabel("Food: " + type.productionCostFood + "  |  " + type.turnsToTrain + " turns");
                costLabel.setForeground(new Color(0xaaaaaa));

                JButton btn = new JButton("Train");
                boolean capFull = !nation.canTrainUnit();
                btn.setEnabled(!capFull && nation.getFood().getBalance() >= type.productionCostFood);
                btn.setBackground(new Color(0x5d4037));
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
        scrollPane.setPreferredSize(new Dimension(500, 550));
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

        JButton returnToMainMenu = new JButton("Return To Main Menu");
        returnToMainMenu.addActionListener(e -> {
            closeActivePopup();
            Game.instance.changeScene(Menus.MAIN_MENU);
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

        for (JButton b : new JButton[]{continueButton, returnToMainMenu, exit}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(220, 40));
            menuPanel.add(Box.createVerticalStrut(15));
            menuPanel.add(b);
        }

        GameController.setSelectedCivilizedUnit(null);
        showPopupPanel(menuPanel);
    }

    public void updateWholeMap() {
        LandController.updateLandVisibility();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                graphicalMap[i][j].updateMap();
            }
        }
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
        buildingWarningLabel.setText(atRiskBuildings + " building(s) will be destroyed next turn (unpaid Wood upkeep) - hover for details");
        buildingWarningLabel.setToolTipText(atRiskBuildings > 0 ? atRiskDetail.toString() : null);
        buildingWarningLabel.setVisible(atRiskBuildings > 0);

        turn.setText("Turn " + Game.instance.getTurn());
        yearLabel.setText("AD " + Game.instance.getTurn() * 50);

        updateProductionPanel(nation);
        updateResearchPanel(nation);
    }

    /**
     * Research in this game is an instant, single-turn action gated on resources
     * (see TownHall.researchTechnology) rather than a multi-turn queue, so there's no
     * "turns remaining" to show here - instead this surfaces the next unresearched,
     * available technology so the player doesn't have to open the Town Hall panel just
     * to see whether anything new is researchable.
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
        sb.append("</html>");
        return sb.toString();
    }

    private void updateProductionPanel(Nation nation) {
        TownHall th = nation.getTownHall();
        if (th != null && th.hasQueuedProduction()) {
            CivilizedUnitType current = th.getCurrentProduction();
            int turnsLeft = th.getTurnsLeftForCurrent();
            productionUnitName.setText(current.name);
            productionTurnsLeft.setText(turnsLeft + " turn" + (turnsLeft == 1 ? "" : "s") + " left");
            double progress = 1.0 - ((double) turnsLeft / current.turnsToTrain);
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

        String message = GameController.endTurn();
        showPopUp(SwingUtilities.getWindowAncestor(root), message);

        hideUnitPopup();
        updateWholeMap();
        updateHud();
    }

    public void hideUnitPopup() {
        GameController.setSelectedCivilizedUnit(null);
        closeActivePopup();
    }
}

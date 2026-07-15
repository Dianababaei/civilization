package sut.civilization.Model.Classes;

import sut.civilization.Civilization;
import sut.civilization.Controller.GameControllers.GameController;
import sut.civilization.View.Graphical.GamePlayController;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.ToolTipManager;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Color;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

// one hexagon tile on the map. draws all the layers (terrain, resource, unit,
// improvement, fog etc) on top of each other and handles clicks for that tile.
// we just use one click handler and check what's on the tile instead of making
// separate clickable components for each layer, seemed simpler
//
// note: the hex shape is always drawn the same way in "local" coordinates
// (like it's own little box), then that box gets moved/resized to the tile's
// actual position on screen. makes the math easier since we don't recompute
// the hexagon points every time
public class LandGraphical extends JComponent {

    private static final double tileRadius = 100;
    private static final int LOCAL_W = (int) (tileRadius * 2);
    private static final int LOCAL_H = (int) (tileRadius * Math.sqrt(3));
    private static final Polygon LOCAL_HEX = buildLocalHex();

    private final Land land;
    private Pair<Integer, Integer> coordinate;
    private final Pair<Double, Double> centerCoordinate = new Pair<>(0.0, 0.0);

    private static final Map<String, Image> imageCache = new HashMap<>();

    private Image terrainImage;
    private Image landFeatureImage;
    private Image resourceImage;
    private Image ironImage;
    private Image civilizedUnitImage;
    private Image improvementImage;
    private Image townHallImage;
    private Image fogImage;
    private float fogOpacity = 0f;
    private boolean fogVisible = false;
    private boolean resourceDepleted = false;
    private boolean ironDepleted = false;
    private boolean ownedByCurrentNation = false;
    /** For each of the 6 edges (edge i = LOCAL_HEX vertex i to vertex i+1), whether that
     *  side faces a hex NOT owned by the current nation and therefore needs a border drawn. */
    private final boolean[] frontierEdge = new boolean[6];

    private String tooltipText = "";

    private MoveClickHandler moveClickHandler;

    public interface MoveClickHandler {
        void onTileClicked(int x, int y);
    }

    private double zoomScale = 1.0;

    private static Polygon buildLocalHex() {
        int cx = LOCAL_W / 2;
        int cy = LOCAL_H / 2;
        int[] xs = new int[6];
        int[] ys = new int[6];
        double half = tileRadius * Math.cos(Math.toRadians(60));
        double vert = tileRadius * Math.sin(Math.toRadians(60));
        xs[0] = (int) (cx - half); ys[0] = (int) (cy - vert);
        xs[1] = (int) (cx + half); ys[1] = (int) (cy - vert);
        xs[2] = (int) (cx + tileRadius); ys[2] = cy;
        xs[3] = (int) (cx + half); ys[3] = (int) (cy + vert);
        xs[4] = (int) (cx - half); ys[4] = (int) (cy + vert);
        xs[5] = (int) (cx - tileRadius); ys[5] = cy;
        return new Polygon(xs, ys, 6);
    }

    public LandGraphical(Pair<Integer, Integer> coordinate, JPanel pane) {
        setCoordinates(coordinate);
        land = Game.instance.map[coordinate.x][coordinate.y];

        terrainImage = loadImage("/sut/civilization/Images/tiles/" + land.getLandType().name + ".png");

        applyBounds();
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick();
            }
        });

        updateMap();
        pane.add(this);
    }

    /** Rescales and repositions this tile for the given discrete zoom level. */
    public void setZoom(double scale) {
        this.zoomScale = scale;
        applyBounds();
        revalidate();
        repaint();
    }

    private void applyBounds() {
        setBounds((int) ((centerCoordinate.x - tileRadius) * zoomScale),
                (int) ((centerCoordinate.y - tileRadius * Math.sqrt(3) / 2) * zoomScale),
                (int) (LOCAL_W * zoomScale),
                (int) (LOCAL_H * zoomScale));
    }

    private static Image loadImage(String path) {
        return imageCache.computeIfAbsent(path, p -> {
            var url = Civilization.class.getResource(p);
            if (url == null) return null;
            return new ImageIcon(url).getImage();
        });
    }

    /** Decides what the click means based on what's actually present on this tile. */
    private void handleClick() {
        if (moveClickHandler != null) {
            moveClickHandler.onTileClicked(coordinate.x, coordinate.y);
            return;
        }

        // if there's a unit on this tile, select it first (even if town hall is
        // also here) - you can still open town hall from the toolbar button
        if (land.getCivilizedUnit() != null) {
            if (land.getCivilizedUnit().getOwnerNation() == GameController.getCurrentTurnUser().getNation()) {
                GameController.setSelectedCivilizedUnit(land.getCivilizedUnit());
                GamePlayController.getInstance().showSelectedCivilizedUnitInfo();
            }
            return;
        }

        for (User u : Game.instance.getPlayersInGame()) {
            Nation n = u.getNation();
            if (n != null && n.getTownHall() != null && n.getTownHall().getMainLand() == land) {
                GamePlayController.getInstance().showTownHallPanel();
                return;
            }
        }
    }

    /** Arms/disarms this tile for move-destination clicks; used by GamePlayController.unitMove(). */
    public void setMoveClickHandler(MoveClickHandler handler) {
        this.moveClickHandler = handler;
    }

    public void updateMap() {
        StringBuilder landInfo = new StringBuilder(land.getLandType().name + " at (" + land.getI() + " , " + land.getJ() + ")");

        if (land.getLandFeature() != null) {
            landFeatureImage = loadImage("/sut/civilization/Images/tiles/" + land.getLandFeature().getLandFeatureType().name + ".png");
            landInfo.append("\nFeature: ").append(land.getLandFeature().getLandFeatureType().name);
        } else landFeatureImage = null;

        if (land.getResource() != null) {
            resourceImage = loadImage("/sut/civilization/Images/Icons/ResourceIcons/" + land.getResource().getResourceType().name + ".png");
            resourceDepleted = land.isResourceDepleted();
            landInfo.append("\nResource: ").append(land.getResource().getResourceType().name);
            landInfo.append(resourceDepleted ? " (depleted)" : " (" + land.getResource().getBalance() + " left)");
        } else {
            resourceImage = null;
            resourceDepleted = false;
        }

        if (land.hasIron()) {
            ironImage = loadImage("/sut/civilization/Images/Icons/ResourceIcons/Iron.png");
            ironDepleted = land.isIronDepleted();
            landInfo.append(ironDepleted ? " + Iron (depleted)" : " + Iron (" + land.getIronResource().getBalance() + " left)");
        } else {
            ironImage = null;
            ironDepleted = false;
        }

        if (land.getCivilizedUnit() != null) {
            String civName = ((CivilizedUnit) land.getCivilizedUnit()).getCivilizedUnitType().name;
            civilizedUnitImage = loadImage("/sut/civilization/Images/units/" + civName + ".png");
        } else civilizedUnitImage = null;

        boolean isTownHallTile = false;
        for (User u : Game.instance.getPlayersInGame()) {
            Nation n = u.getNation();
            if (n != null && n.getTownHall() != null && n.getTownHall().getMainLand() == land) {
                isTownHallTile = true;
                break;
            }
        }
        if (isTownHallTile) {
            townHallImage = loadImage("/sut/civilization/Images/tiles/TownHall.png");
            landInfo.append("\nTown Hall");
        } else townHallImage = null;

        if (land.getImprovement() != null) {
            improvementImage = loadImage("/sut/civilization/Images/tiles/" + land.getImprovement().getImprovementType().name + ".png");
            landInfo.append("\nImprovement: ").append(land.getImprovement().getImprovementType().name);
            int missed = land.getImprovement().getMissedUpkeepTurns();
            if (missed > 0) {
                landInfo.append("\nUpkeep unpaid ").append(missed).append("/3 turns")
                        .append(missed >= 2 ? " - will be destroyed next turn!" : "!");
            }
        } else improvementImage = null;

        if (land.getVisibility() == 1) {
            fogImage = loadImage("/sut/civilization/Images/tiles/fog.png");
            fogOpacity = 0.7f;
            fogVisible = true;
        } else if (land.getVisibility() == 0) {
            fogImage = loadImage("/sut/civilization/Images/tiles/fog.png");
            fogOpacity = 1f;
            fogVisible = true;
        } else {
            fogImage = null;
            fogVisible = false;
        }

        Nation currentNation = GameController.getCurrentTurnUser() != null
                ? GameController.getCurrentTurnUser().getNation() : null;
        ownedByCurrentNation = currentNation != null && currentNation.ownsHex(land.getI(), land.getJ());
        if (ownedByCurrentNation) landInfo.append("\n(Your territory)");

        if (ownedByCurrentNation) {
            var neighbors = sut.civilization.Controller.GameControllers.LandController.getAllNeighborsIndexes(coordinate);
            for (int i = 0; i < 6; i++) {
                Pair<Integer, Integer> nb = neighbors.get(i);
                frontierEdge[i] = !Pair.isValid(nb) || !currentNation.ownsHex(nb.x, nb.y);
            }
        }

        tooltipText = landInfo.toString();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return tooltipText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // This component's own bounds already reflect zoomScale (see applyBounds), so Swing
        // hands us a canvas of exactly (LOCAL_W*zoomScale, LOCAL_H*zoomScale) pixels starting
        // at (0,0). Scaling the graphics context lets every draw call below use the fixed,
        // unscaled LOCAL_HEX/LOCAL_W/LOCAL_H constants regardless of zoom level.
        g2.scale(zoomScale, zoomScale);

        int cx = LOCAL_W / 2;
        int cy = LOCAL_H / 2;

        java.awt.Shape oldClip = g2.getClip();
        g2.clip(LOCAL_HEX);
        if (terrainImage != null) {
            g2.drawImage(terrainImage, 0, 0, LOCAL_W, LOCAL_H, this);
        } else {
            g2.setColor(new Color(60, 60, 60));
            g2.fillPolygon(LOCAL_HEX);
        }
        g2.setClip(oldClip);

        g2.setColor(new Color(60, 45, 20));
        g2.drawPolygon(LOCAL_HEX);

        if (ownedByCurrentNation) {
            java.awt.Stroke oldStroke = g2.getStroke();
            int[] xs = LOCAL_HEX.xpoints;
            int[] ys = LOCAL_HEX.ypoints;

            // Dark outline first (contrast against light terrain), then a bright core on top
            // (contrast against dark terrain) - this "halo" trick keeps the border readable
            // regardless of what's underneath it.
            g2.setStroke(new java.awt.BasicStroke(9f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0x3a2200));
            for (int i = 0; i < 6; i++) {
                if (!frontierEdge[i]) continue;
                int next = (i + 1) % 6;
                g2.drawLine(xs[i], ys[i], xs[next], ys[next]);
            }

            g2.setStroke(new java.awt.BasicStroke(5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xffb020));
            for (int i = 0; i < 6; i++) {
                if (!frontierEdge[i]) continue;
                int next = (i + 1) % 6;
                g2.drawLine(xs[i], ys[i], xs[next], ys[next]);
            }
            g2.setStroke(oldStroke);
        }

        if (landFeatureImage != null) {
            int s = (int) (tileRadius * 1.5);
            g2.drawImage(landFeatureImage, cx - s / 2, cy - s / 2, s, s, this);
        }

        if (resourceImage != null) {
            int s = (int) (tileRadius * 0.5);
            int rx = cx;
            int ry = cy - (int) (tileRadius * 0.75);
            if (resourceDepleted) {
                java.awt.Composite oldComposite = g2.getComposite();
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
                g2.drawImage(resourceImage, rx, ry, s, s, this);
                g2.setComposite(oldComposite);
                g2.setColor(Color.RED);
                g2.drawLine(rx, ry, rx + s, ry + s);
                g2.drawLine(rx + s, ry, rx, ry + s);
            } else {
                g2.drawImage(resourceImage, rx, ry, s, s, this);
            }
        }

        if (ironImage != null) {
            int s = (int) (tileRadius * 0.35);
            int ix = cx + (int) (tileRadius * 0.35);
            int iy = cy - (int) (tileRadius * 0.75);
            if (ironDepleted) {
                java.awt.Composite oldComposite = g2.getComposite();
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
                g2.drawImage(ironImage, ix, iy, s, s, this);
                g2.setComposite(oldComposite);
                g2.setColor(Color.RED);
                g2.drawLine(ix, iy, ix + s, iy + s);
                g2.drawLine(ix + s, iy, ix, iy + s);
            } else {
                g2.drawImage(ironImage, ix, iy, s, s, this);
            }
        }

        if (civilizedUnitImage != null) {
            int s = (int) (tileRadius * 0.5);
            g2.drawImage(civilizedUnitImage, cx - (int) (tileRadius * 0.5), cy - s / 2, s, s, this);

            // small green dot so you can tell a stationed worker apart from just
            // an idle one standing on the tile (they look the same otherwise)
            CivilizedUnit occupant = land.getCivilizedUnit();
            if (occupant != null && occupant.isStationed()) {
                int dotSize = (int) (tileRadius * 0.16);
                int dotX = cx - (int) (tileRadius * 0.5) + s - dotSize;
                int dotY = cy - s / 2;
                g2.setColor(new Color(0x39d353));
                g2.fillOval(dotX, dotY, dotSize, dotSize);
                g2.setColor(new Color(0x0c3d1e));
                g2.drawOval(dotX, dotY, dotSize, dotSize);
            }
        }

        if (townHallImage != null) {
            int s = (int) (tileRadius * 0.5);
            g2.drawImage(townHallImage, cx, cy, s, s, this);
        }

        if (improvementImage != null) {
            int s = (int) (tileRadius * 0.5);
            g2.drawImage(improvementImage, cx, cy + s / 2, s, s, this);
        }

        if (fogVisible && fogImage != null) {
            java.awt.Composite oldComposite = g2.getComposite();
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, fogOpacity));
            g2.clip(LOCAL_HEX);
            g2.drawImage(fogImage, 0, 0, LOCAL_W, LOCAL_H, this);
            g2.setComposite(oldComposite);
            g2.setClip(oldClip);
        }

        g2.dispose();
    }

    private void setCoordinates(Pair<Integer, Integer> coordinate) {
        this.coordinate = coordinate;

        if (coordinate.x % 2 == 0) {
            this.centerCoordinate.x = (coordinate.x / 2) * 3 * tileRadius + tileRadius;
            this.centerCoordinate.y = coordinate.y * 2 * tileRadius * Math.sin(Math.toRadians(60)) + tileRadius * Math.sin(Math.toRadians(60));
        } else {
            this.centerCoordinate.x = ((coordinate.x - 1) / 2) * 3 * tileRadius + 2 * tileRadius + Math.cos(Math.toRadians(60)) * tileRadius;
            this.centerCoordinate.y = coordinate.y * 2 * tileRadius * Math.sin(Math.toRadians(60)) + 2 * tileRadius * Math.sin(Math.toRadians(60));
        }
    }

    public Land getLand() {
        return land;
    }

    public Pair<Integer, Integer> getCoordinate() {
        return coordinate;
    }

    public Pair<Double, Double> getCenterCoordinate() {
        return centerCoordinate;
    }
}

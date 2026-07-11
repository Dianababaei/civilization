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

/**
 * A single clickable hex tile. Layers everything (terrain, feature, resource, iron,
 * unit, improvement, town hall, fog) in one paintComponent and dispatches a
 * single mouseClicked handler that decides intent from whatever is actually present
 * at the tile - this avoids stacking several overlapping clickable components that
 * could swallow clicks meant for what's underneath.
 *
 * All drawing math below works in a fixed LOCAL coordinate space: a bounding box of
 * width 2*tileRadius, height sqrt(3)*tileRadius, with the hex centered inside it.
 * That local hex polygon never changes - only this component's on-screen bounds
 * (set by applyBounds) move/scale it around the map. This keeps painting independent
 * of the component's absolute position, so there is no coordinate-space mixing between
 * "where this tile sits on the map" and "how to draw the hex inside its own bounds".
 */
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
            g2.setStroke(new java.awt.BasicStroke(4f));
            g2.setColor(new Color(0xf0c060));
            g2.drawPolygon(LOCAL_HEX);
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

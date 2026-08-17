import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;
import sut.civilization.View.Graphical.GamePlayController;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Scripted end-to-end UI tour of the Phase 2 features. Boots into the game scene,
 * performs real gameplay steps (through the same controllers the buttons call),
 * screenshots after each step, auto-dismisses confirm dialogs, then exits.
 * Usage: java VisualTour <outputDir>
 */
public class VisualTour {

    static JFrame frame;
    static File outDir;
    static GamePlayController gpc;
    static Nation nation;

    public static void main(String[] args) {
        outDir = new File(args.length > 0 ? args[0] : "tour");
        outDir.mkdirs();

        User user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);
        Game.instance.setMap(LandController.mapInitializer());
        LandController.initializeDistances();
        LandController.updateDistances();
        nation = user.getNation();

        SwingUtilities.invokeLater(VisualTour::start);
    }

    static void start() {
        frame = new JFrame("Civilization V - Visual Tour");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1366, 768);
        frame.setLocationRelativeTo(null);
        CardLayout cardLayout = new CardLayout();
        JPanel screenContainer = new JPanel(cardLayout);
        Game.instance.initWindow(frame, cardLayout, screenContainer, menu -> {
            if (menu == Menus.GAME_MENU) return GamePlayController.build();
            return sut.civilization.View.Graphical.MainController.build();
        });
        frame.add(screenContainer);
        frame.setVisible(true);
        Game.instance.changeScene(Menus.GAME_MENU);
        gpc = GamePlayController.getInstance();

        // keep confirm dialogs from blocking the tour - auto-press their Yes button
        Timer dialogDismisser = new Timer(400, e -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible()) clickYes((JDialog) w);
            }
        });
        dialogDismisser.start();

        int t = 1500;
        // 1. debug: fill resources
        step(t, () -> { CheatController.debugAddAllResources(200); gpc.updateHud(); });
        step(t += 800, () -> shoot("01-resources-full"));

        // 2. queue the town hall upgrade, show the busy panel + cancel button
        step(t += 500, () -> { TownHallController.upgradeTownHall(); gpc.showTownHallPanel(); });
        step(t += 1500, () -> shoot("02-upgrade-in-queue"));

        // 3. skip 3 turns -> Settlement; panel should show level 2, HP 250, cap 15
        step(t += 500, () -> {
            CheatController.debugAdvanceTurns(3);
            gpc.updateWholeMap(); gpc.updateHud(); gpc.showTownHallPanel();
        });
        step(t += 2000, () -> shoot("03-settlement-level2"));

        // 4. place a Bazaar on a free territory tile, open the trade panel
        step(t += 500, () -> {
            placeImprovement(ImprovementType.BAZAAR);
            gpc.updateWholeMap(); gpc.showTradePanel();
        });
        step(t += 1500, () -> shoot("04-trade-ready"));

        // 5. sell 10 food at 50% -> +5 wood; panel reopens showing "already traded"
        step(t += 500, () -> {
            System.out.println("trade: " + TradeController.tradeAtBazaar(ResourceType.FOOD, ResourceType.WOOD, 10));
            gpc.updateHud(); gpc.showTradePanel();
        });
        step(t += 1500, () -> shoot("05-bazaar-used"));

        // 6. monument on a plain territory tile -> happiness +2 in the HUD
        step(t += 500, () -> {
            placeImprovement(ImprovementType.MONUMENT);
            gpc.updateWholeMap(); gpc.updateHud(); gpc.showDebugPanel();
        });
        step(t += 1500, () -> shoot("06-monument-happiness"));

        // 7. real End Turn through the UI -> multi-line turn report toast
        step(t += 500, () -> {
            TownHallController.enqueueUnit("Worker"); // 1 turn - finishes this End Turn
            gpc.nextTurn();
        });
        step(t += 2200, () -> shoot("07-turn-report"));

        step(t += 800, () -> System.exit(0));
    }

    /** Puts the improvement on the first suitable territory tile (bypasses Builder AP for the demo). */
    static void placeImprovement(ImprovementType type) {
        for (Pair<Integer, Integer> hex : nation.getTerritory()) {
            if (!Pair.isValid(hex)) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            if (tile.getImprovement() != null) continue;
            if (nation.getTownHall() != null && nation.getTownHall().getMainLand() == tile) continue;
            if (type.requiredLandType != null && tile.getLandType() != type.requiredLandType) continue;
            tile.setImprovement(new Improvement(type));
            System.out.println("placed " + type.name + " at (" + hex.x + "," + hex.y + ")");
            return;
        }
        System.out.println("no free tile for " + type.name);
    }

    static void clickYes(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JButton) {
                String txt = ((JButton) comp).getText();
                if ("Yes".equalsIgnoreCase(txt) || "OK".equalsIgnoreCase(txt)) {
                    ((JButton) comp).doClick();
                    return;
                }
            } else if (comp instanceof Container) {
                clickYes((Container) comp);
            }
        }
    }

    static void step(int delayMs, Runnable action) {
        Timer timer = new Timer(delayMs, e -> {
            try {
                action.run();
            } catch (Throwable ex) {
                System.out.println("STEP FAILED at +" + delayMs + "ms:");
                ex.printStackTrace(System.out);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    static void shoot(String name) {
        try {
            frame.toFront(); // make sure nothing covers the game when we capture
            System.out.println("state before " + name + ": gameTurn=" + Game.instance.getTurn()
                    + " hudTurnLabel='" + gpc.turn.getText() + "'");
            BufferedImage img = new Robot().createScreenCapture(frame.getBounds());
            ImageIO.write(img, "png", new File(outDir, name + ".png"));
            System.out.println("shot: " + name);
        } catch (Exception ex) {
            System.out.println("screenshot failed: " + ex);
        }
    }
}

import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.*;
import sut.civilization.View.Graphical.GamePlayController;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Boots straight into the game scene (skipping menus) and takes a few screenshots
 * so UI changes can be reviewed without clicking through the app. Exits by itself.
 * Usage: java VisualDemo <outputDir>
 */
public class VisualDemo {

    static JFrame frame;
    static File outDir;

    public static void main(String[] args) throws Exception {
        outDir = new File(args.length > 0 ? args[0] : "screenshots");
        outDir.mkdirs();

        // same headless setup as Phase2Driver / the real new-game flow
        User user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);
        Game.instance.setMap(LandController.mapInitializer());
        LandController.initializeDistances();
        LandController.updateDistances();

        SwingUtilities.invokeLater(VisualDemo::start);
    }

    static void start() {
        frame = new JFrame("Civilization V - Visual Demo");
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

        GamePlayController gpc = GamePlayController.getInstance();

        // scripted screenshot tour; generous delays so painting settles
        step(1500, () -> shoot("01-game-spring"));
        step(2500, () -> {
            Game.instance.setTurn(35); // winter
            gpc.updateWholeMap();
            gpc.updateHud();
        });
        step(4000, () -> shoot("02-game-winter"));
        step(4500, () -> {
            Game.instance.setTurn(1);
            gpc.updateWholeMap();
            gpc.updateHud();
            gpc.showTownHallPanel();
        });
        step(6000, () -> shoot("03-townhall-panel"));
        step(6500, gpc::showTradePanel);
        step(8000, () -> shoot("04-trade-panel"));
        step(8500, gpc::showDebugPanel);
        step(10000, () -> shoot("05-debug-panel"));
        step(11000, () -> System.exit(0));
    }

    static void step(int delayMs, Runnable action) {
        Timer t = new Timer(delayMs, e -> action.run());
        t.setRepeats(false);
        t.start();
    }

    static void shoot(String name) {
        try {
            Rectangle bounds = frame.getBounds();
            BufferedImage img = new Robot().createScreenCapture(bounds);
            ImageIO.write(img, "png", new File(outDir, name + ".png"));
            System.out.println("shot: " + name);
        } catch (Exception ex) {
            System.out.println("screenshot failed: " + ex);
        }
    }
}

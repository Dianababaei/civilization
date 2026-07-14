package sut.civilization;

import sut.civilization.Controller.MainController;
import sut.civilization.Enums.Menus;
import sut.civilization.Model.Classes.Game;
import sut.civilization.View.Graphical.GamePlayController;

import javax.swing.*;
import java.awt.*;

public class Civilization {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Civilization::start);
    }

    private static void start() {
        JFrame frame = new JFrame("Civilization V");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1366, 768);
        frame.setLocationRelativeTo(null);

        var iconUrl = Civilization.class.getResource("Images/Icons/programIcon.png");
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }

        CardLayout cardLayout = new CardLayout();
        JPanel screenContainer = new JPanel(cardLayout);

        Game.instance.initWindow(frame, cardLayout, screenContainer, menu -> {
            if (menu == Menus.GAME_MENU) return GamePlayController.build();
            return sut.civilization.View.Graphical.MainController.build();
        });

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(frame, "Are you sure you want to exit?",
                        "Exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    sut.civilization.View.Graphical.MainController.stopMusic();
                    new MainController().exitGame();
                }
            }
        });

        frame.add(screenContainer);
        frame.setVisible(true);

        Game.instance.changeScene(Menus.MAIN_MENU);
    }
}

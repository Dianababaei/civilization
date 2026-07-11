package sut.civilization.View.Graphical;

import javax.swing.*;
import java.awt.*;

public class ViewController {
    String resultMessage;

    /** Non-blocking toast in the bottom-left of the window: green border on success, red on failure. */
    protected void showPopUp(Window window, String resultMessage) {
        if (window == null || resultMessage == null) return;

        JWindow popup = new JWindow(window);
        JLabel label = new JLabel(resultMessage);
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setBorder(BorderFactory.createLineBorder(
                isMessageError(resultMessage) ? new Color(0xff0000) : new Color(0x59ff00), 1));
        label.setPreferredSize(new Dimension(300, 40));

        popup.getContentPane().add(label);
        popup.pack();
        popup.setLocation(window.getX() + 10, window.getY() + window.getHeight() - 52);
        popup.setVisible(true);

        Timer timer = new Timer(2200, e -> popup.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    protected boolean isMessageError(String message) {
        return message.charAt(message.length() - 1) == '!';
    }
}

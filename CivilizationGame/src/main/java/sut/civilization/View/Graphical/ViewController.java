package sut.civilization.View.Graphical;

import javax.swing.*;
import java.awt.*;

public class ViewController {
    String resultMessage;

    /** Non-blocking toast in the bottom-left of the window: green border on success, red on failure.
     *  Multi-line HTML messages ("<html>a<br>b</html>") grow the toast and stay visible longer. */
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

        int lines = 1;
        for (int i = 0; (i = resultMessage.indexOf("<br>", i)) != -1; i += 4) lines++;
        int height = Math.max(40, 22 + lines * 19);
        label.setPreferredSize(new Dimension(lines > 1 ? 420 : 300, height));

        popup.getContentPane().add(label);
        popup.pack();
        popup.setLocation(window.getX() + 10, window.getY() + window.getHeight() - height - 12);
        popup.setVisible(true);

        Timer timer = new Timer(lines > 1 ? 4500 : 2200, e -> popup.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    protected boolean isMessageError(String message) {
        return !message.isEmpty() && message.charAt(message.length() - 1) == '!';
    }
}

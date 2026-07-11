package sut.civilization.Enums;

import sut.civilization.Civilization;

import javax.swing.*;
import java.util.Objects;

public enum CommonIcons {
    EX(new ImageIcon(Objects.requireNonNull(Civilization.class.getResource(
            "/sut/civilization/Images/otherIcons/ex.png"
    ))))
    ;

    public final ImageIcon icon;

    CommonIcons(ImageIcon icon) {
        this.icon = icon;
    }
}

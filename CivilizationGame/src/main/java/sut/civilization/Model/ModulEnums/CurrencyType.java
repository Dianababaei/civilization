package sut.civilization.Model.ModulEnums;

import java.awt.Color;

public enum CurrencyType {
    FOOD("Food",   new Color(144, 238, 144)),
    WOOD("Wood",   new Color(139, 69, 19)),
    STONE("Stone", new Color(211, 211, 211)),
    IRON("Iron",   new Color(70, 130, 180));

    public final String name;
    public final Color color;

    CurrencyType(String name, Color color) {
        this.name = name;
        this.color = color;
    }
}

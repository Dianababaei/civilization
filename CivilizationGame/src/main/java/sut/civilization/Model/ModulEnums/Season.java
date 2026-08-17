package sut.civilization.Model.ModulEnums;

/**
 * Phase 2 seasons: exactly 10 turns each, cycling Spring -> Summer -> Autumn -> Winter.
 * Turn 1-10 = Spring, 11-20 = Summer, 21-30 = Autumn, 31-40 = Winter, 41-50 = Spring...
 * Effects (per spec): Spring farms & stables +1 food; Summer none; Autumn +1 AP on water
 * movement (no sea yet); Winter farms -1 food and +1 AP movement on every land hex.
 */
public enum Season {
    SPRING("Spring"),
    SUMMER("Summer"),
    AUTUMN("Autumn"),
    WINTER("Winter");

    public final String name;

    Season(String name) {
        this.name = name;
    }

    public static Season forTurn(int turn) {
        if (turn < 1) turn = 1;
        return values()[((turn - 1) / 10) % 4];
    }

    /** Short effect text for the HUD tooltip / notifications. */
    public String effectText() {
        switch (this) {
            case SPRING: return "farms & stables +1 food";
            case AUTUMN: return "storm winds (water movement +1 AP)";
            case WINTER: return "farms -1 food, moving costs +1 AP";
            default:     return "no special effect";
        }
    }
}

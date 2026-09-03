package sut.civilization.Model.ModulEnums;

/**
 * Phase 3 PvP diplomacy (P3-38..40). Every pair of players starts NEUTRAL;
 * a war declaration flips the pair to ENEMY, a two-sided agreement to ALLY.
 * The server is the only one allowed to change these.
 */
public enum Relation {
    NEUTRAL("Neutral"),
    ENEMY("Enemy"),
    ALLY("Ally");

    public final String label;

    Relation(String label) {
        this.label = label;
    }
}

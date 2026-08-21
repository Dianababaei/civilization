package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.EnemyType;

/**
 * A hostile occupant of one hex (one enemy per hex, same simplification as our own
 * units). A CAMP additionally carries a barbarian guard: while the guard lives,
 * attacking the camp hex is a dice battle against the guard - only after the guard
 * dies can the camp structure itself be damaged (P2-16).
 */
public class Enemy implements java.io.Serializable {

    public static final int CAMP_GUARD_HP = 2;

    private final EnemyType type;
    private int hp;
    /** CAMP only: hits the defending barbarian guard can still take. */
    private int guardHp;
    private final Pair<Integer, Integer> location;

    public Enemy(EnemyType type, Pair<Integer, Integer> location) {
        this.type = type;
        this.hp = type.maxHp;
        this.guardHp = type == EnemyType.CAMP ? CAMP_GUARD_HP : 0;
        this.location = location;
    }

    public EnemyType getType() { return type; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getGuardHp() { return guardHp; }
    public void setGuardHp(int guardHp) { this.guardHp = guardHp; }
    public Pair<Integer, Integer> getLocation() { return location; }

    /** True while this camp's guard is alive - the structure can't be hit yet. */
    public boolean hasLivingGuard() {
        return type == EnemyType.CAMP && guardHp > 0;
    }
}

package sut.civilization.Model.ModulEnums;

/**
 * Phase 2 combat (P2-14..17): hostile things you can fight. Barbarians stand in for
 * the tribe warriors (2 defense dice per spec), bears are the wild animals (1 die),
 * and the camp is an enemy STRUCTURE - no dice, it takes flat attack-power damage
 * once its guard is dead.
 */
public enum EnemyType {
    //          name              maxHp  defenseDice  attackPower
    BARBARIAN(  "Barbarian",      2,     2,           15),
    // P2-57: the bear is an HP tank (120) - our dice wins chip it by ATTACK POWER,
    // not by 1 hit like the hit-scale barbarian
    BEAR(       "Bear",           120,   1,           35),
    CAMP(       "Barbarian Camp", 50,    0,           0);

    public final String name;
    /** Barbarian: hits it survives. Bear/Camp: an HP pool. */
    public final int maxHp;
    /** Dice rolled when defending (spec: barbarian 2, wild animal 1, structure none). */
    public final int defenseDice;
    /** Barbarian: flat damage to our Town Hall. Bear: direct HP damage to civilian units
     *  (spec P2-57: bears never damage buildings). */
    public final int attackPower;

    EnemyType(String name, int maxHp, int defenseDice, int attackPower) {
        this.name = name;
        this.maxHp = maxHp;
        this.defenseDice = defenseDice;
        this.attackPower = attackPower;
    }
}

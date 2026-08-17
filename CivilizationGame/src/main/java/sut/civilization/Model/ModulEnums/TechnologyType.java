package sut.civilization.Model.ModulEnums;

/**
 * Town Hall upgrade tree (Phase 1 + Phase 2).
 * Each entry defines: display name, resource costs, research time (turns in the
 * production queue), prerequisites, and the Town Hall level needed to start it.
 */
public enum TechnologyType {
    //                         name                      woodCost stoneCost ironCost turns  thLevel  prerequisite
    STORAGE_UPGRADE_1("Storage Upgrade I",               5,       3,        0,       2,     1,       null),
    STORAGE_UPGRADE_2("Storage Upgrade II",              8,       5,        2,       2,     1,       STORAGE_UPGRADE_1),
    TECH_STONE_MINE(  "Stone Mine Technology",           4,       0,        0,       1,     1,       null),
    TECH_IRON_MINE(   "Iron Mine Technology",            4,       2,        0,       2,     1,       TECH_STONE_MINE),
    // Phase 2: Steel Tools - 1.5x output for stone/iron mines, needs Settlement (level 2)
    TECH_TOOLS(       "Steel Tools",                     0,       0,        40,      3,     2,       TECH_IRON_MINE),
    TECH_TOWN(        "Town Construction Technology",    6,       4,        2,       2,     1,       null);

    public final String name;
    public final int woodCost;
    public final int stoneCost;
    public final int ironCost;
    /** Turns this research occupies the Town Hall production queue. */
    public final int turnsToResearch;
    /** Minimum Town Hall level required to start this research. */
    public final int requiredTownHallLevel;
    /** Must be researched before this one becomes available. null = always available. */
    public final TechnologyType prerequisite;

    TechnologyType(String name, int woodCost, int stoneCost, int ironCost,
                   int turnsToResearch, int requiredTownHallLevel, TechnologyType prerequisite) {
        this.name = name;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
        this.ironCost = ironCost;
        this.turnsToResearch = turnsToResearch;
        this.requiredTownHallLevel = requiredTownHallLevel;
        this.prerequisite = prerequisite;
    }

    public boolean isAvailable(java.util.Map<TechnologyType, Boolean> unlocked) {
        if (prerequisite == null) return true;
        return Boolean.TRUE.equals(unlocked.get(prerequisite));
    }
}

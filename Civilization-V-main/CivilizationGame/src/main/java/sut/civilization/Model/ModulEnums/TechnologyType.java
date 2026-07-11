package sut.civilization.Model.ModulEnums;

/**
 * Town Hall upgrade tree (Phase 1).
 * Each entry defines: display name, resource costs, prerequisites, and what it unlocks.
 */
public enum TechnologyType {
    //                         name                      woodCost stoneCost ironCost  prerequisite
    STORAGE_UPGRADE_1("Storage Upgrade I",               5,       3,        0,        null),
    STORAGE_UPGRADE_2("Storage Upgrade II",              8,       5,        2,        STORAGE_UPGRADE_1),
    TECH_STONE_MINE(  "Stone Mine Technology",           4,       0,        0,        null),
    TECH_IRON_MINE(   "Iron Mine Technology",            4,       2,        0,        TECH_STONE_MINE),
    TECH_TOOLS(       "Professional Tools",              5,       3,        2,        TECH_IRON_MINE),
    TECH_TOWN(        "Town Construction Technology",    6,       4,        2,        null);

    public final String name;
    public final int woodCost;
    public final int stoneCost;
    public final int ironCost;
    /** Must be researched before this one becomes available. null = always available. */
    public final TechnologyType prerequisite;

    TechnologyType(String name, int woodCost, int stoneCost, int ironCost, TechnologyType prerequisite) {
        this.name = name;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
        this.ironCost = ironCost;
        this.prerequisite = prerequisite;
    }

    public boolean isAvailable(java.util.Map<TechnologyType, Boolean> unlocked) {
        if (prerequisite == null) return true;
        return Boolean.TRUE.equals(unlocked.get(prerequisite));
    }
}

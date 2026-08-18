package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ResourceType;
import sut.civilization.Model.ModulEnums.TechnologyType;

import java.util.ArrayList;
import java.util.HashMap;

public class Nation implements java.io.Serializable {

    // ── Resources ─────────────────────────────────────────────────────────────
    // these starting amounts are not fixed by the project spec, we just picked
    // numbers that felt fair for the first few turns (enough to not starve
    // immediately but still need to build farms etc.)
    private static final int INITIAL_CAPACITY = 200;
    private static final int INITIAL_FOOD  = 25;
    private static final int INITIAL_WOOD  = 20;
    private static final int INITIAL_STONE = 7;
    private static final int INITIAL_IRON  = 0;

    private final Currency food;
    private final Currency wood;
    private final Currency stone;
    private final Currency iron;

    // ── Unit Cap ──────────────────────────────────────────────────────────────
    private static final int INITIAL_UNIT_CAP = 10;
    private static final int UNIT_CAP_PER_TOWN = 5;
    private int baseUnitCap = INITIAL_UNIT_CAP;
    private int extraUnitCap = 0;   // increased by building Towns

    // ── Starvation tracking ───────────────────────────────────────────────────
    private boolean inStarvation = false;

    // ── Technology unlocks ────────────────────────────────────────────────────
    private final HashMap<TechnologyType, Boolean> technologies = new HashMap<>();

    // ── Units & territory ────────────────────────────────────────────────────
    private final ArrayList<Unit> units = new ArrayList<>();
    // hex coords that belong to this player's territory
    private final ArrayList<Pair<Integer, Integer>> territory = new ArrayList<>();

    // ── Town Hall ─────────────────────────────────────────────────────────────
    private TownHall townHall;

    public Nation() {
        food  = new Currency(ResourceType.FOOD,  INITIAL_FOOD,  INITIAL_CAPACITY);
        wood  = new Currency(ResourceType.WOOD,  INITIAL_WOOD,  INITIAL_CAPACITY);
        stone = new Currency(ResourceType.STONE, INITIAL_STONE, INITIAL_CAPACITY);
        iron  = new Currency(ResourceType.IRON,  INITIAL_IRON,  INITIAL_CAPACITY);

        for (TechnologyType t : TechnologyType.values()) {
            technologies.put(t, false);
        }
    }

    // ── Resource accessors ────────────────────────────────────────────────────

    public Currency getResource(ResourceType type) {
        switch (type) {
            case FOOD:  return food;
            case WOOD:  return wood;
            case STONE: return stone;
            case IRON:  return iron;
            default: throw new IllegalArgumentException("Unknown resource: " + type);
        }
    }

    public Currency getFood()  { return food; }
    public Currency getWood()  { return wood; }
    public Currency getStone() { return stone; }
    public Currency getIron()  { return iron; }

    // checks if we can afford this, doesn't spend anything if any amount is short
    public boolean canAfford(int foodCost, int woodCost, int stoneCost, int ironCost) {
        return food.getBalance()  >= foodCost
            && wood.getBalance()  >= woodCost
            && stone.getBalance() >= stoneCost
            && iron.getBalance()  >= ironCost;
    }

    public boolean spend(int foodCost, int woodCost, int stoneCost, int ironCost) {
        if (!canAfford(foodCost, woodCost, stoneCost, ironCost)) return false;
        food.spend(foodCost);
        wood.spend(woodCost);
        stone.spend(stoneCost);
        iron.spend(ironCost);
        return true;
    }

    // ── Unit Cap ──────────────────────────────────────────────────────────────
    // phase 2: the town hall level also raises the cap (+5 per level above 1)

    private static final int UNIT_CAP_PER_TH_LEVEL = 5;

    public int getUnitCap() {
        int thBonus = (townHall == null) ? 0 : (townHall.getLevel() - 1) * UNIT_CAP_PER_TH_LEVEL;
        return baseUnitCap + extraUnitCap + thBonus;
    }
    public int getUnitCount() { return units.size(); }
    public boolean canTrainUnit() { return units.size() < getUnitCap(); }
    public void addTownUnitCapBonus() { extraUnitCap += UNIT_CAP_PER_TOWN; }
    public void removeTownUnitCapBonus() { extraUnitCap = Math.max(0, extraUnitCap - UNIT_CAP_PER_TOWN); }

    // player picks the starting unit cap at game start (not a fixed number), call this
    // before any town bonuses get applied
    public void setBaseUnitCap(int baseUnitCap) { this.baseUnitCap = baseUnitCap; }

    // ── Military unit cap (Phase 2, P2-13) ────────────────────────────────────
    // separate from the total cap: 5 military units per town hall level (5/10/15).
    // military units still count against the total cap too.

    private static final int MILITARY_CAP_PER_TH_LEVEL = 5;

    public int getMilitaryUnitCap() {
        return (townHall == null ? 1 : townHall.getLevel()) * MILITARY_CAP_PER_TH_LEVEL;
    }

    public int getMilitaryUnitCount() {
        int count = 0;
        for (Unit u : units) {
            if (u instanceof CivilizedUnit
                    && ((CivilizedUnit) u).getCivilizedUnitType().isMilitary) count++;
        }
        return count;
    }

    public boolean canTrainMilitaryUnit() {
        return getMilitaryUnitCount() < getMilitaryUnitCap();
    }

    // ── Starvation ────────────────────────────────────────────────────────────

    public boolean isInStarvation() { return inStarvation; }
    public void setInStarvation(boolean v) { this.inStarvation = v; }

    // ── Storage capacity (upgraded via Town Hall) ─────────────────────────────
    // storage upgrade 1 & 2 only bump wood/stone/iron capacity, food stays at base
    // (that's just how the spec lists it)

    public void upgradeStorageCapacity(int bonus) {
        wood.setCapacity(wood.getCapacity()   + bonus);
        stone.setCapacity(stone.getCapacity() + bonus);
        iron.setCapacity(iron.getCapacity()   + bonus);
    }

    // ── Technologies ──────────────────────────────────────────────────────────

    public boolean hasTechnology(TechnologyType t) {
        return Boolean.TRUE.equals(technologies.get(t));
    }

    public void unlockTechnology(TechnologyType t) {
        technologies.put(t, true);
    }

    public HashMap<TechnologyType, Boolean> getTechnologies() { return technologies; }

    // ── Units ─────────────────────────────────────────────────────────────────

    public ArrayList<Unit> getUnits() { return units; }
    public void addUnit(Unit u) { units.add(u); }
    public void removeUnit(Unit u) { units.remove(u); }

    // ── Territory ─────────────────────────────────────────────────────────────

    public ArrayList<Pair<Integer, Integer>> getTerritory() { return territory; }

    public void addToTerritory(Pair<Integer, Integer> hex) {
        if (!territory.contains(hex)) territory.add(hex);
    }

    public boolean ownsHex(int i, int j) {
        return territory.contains(new Pair<>(i, j));
    }

    // ── Town Hall ─────────────────────────────────────────────────────────────

    public TownHall getTownHall() { return townHall; }
    public void setTownHall(TownHall townHall) { this.townHall = townHall; }

    // ── Trade (Phase 2) ───────────────────────────────────────────────────────
    // one transaction per turn per facility; flags reset in nextGameTurn

    private boolean bazaarUsedThisTurn = false;
    private boolean tradingPostUsedThisTurn = false;

    public boolean isBazaarUsedThisTurn() { return bazaarUsedThisTurn; }
    public void setBazaarUsedThisTurn(boolean v) { bazaarUsedThisTurn = v; }
    public boolean isTradingPostUsedThisTurn() { return tradingPostUsedThisTurn; }
    public void setTradingPostUsedThisTurn(boolean v) { tradingPostUsedThisTurn = v; }

    public void resetTradeFlags() {
        bazaarUsedThisTurn = false;
        tradingPostUsedThisTurn = false;
    }

    public boolean hasImprovementInTerritory(sut.civilization.Model.ModulEnums.ImprovementType type) {
        for (Pair<Integer, Integer> hex : territory) {
            if (!Pair.isValid(hex)) continue;
            Improvement imp = Game.instance.map[hex.x][hex.y].getImprovement();
            if (imp != null && imp.getImprovementType() == type) return true;
        }
        return false;
    }

    // ── Happiness (Phase 2) ───────────────────────────────────────────────────
    // Recomputed from current state every call (no stored counter to go stale):
    //   -1 per Town (each new settlement makes people grumpy)
    //   -1 while the unit cap is maxed out (crowding)
    //   -1 while the MILITARY cap is maxed out (P2-25)
    //   +2 per Monument
    //   +1 if a MILITARY unit is garrisoned on the Town Hall tile (people feel safe)
    // Tiers: >= +3 Golden Age (+10% production) | -3..-4 workers each produce 1 less |
    //        <= -5 riot: worker penalty AND every unit loses 1 AP per turn

    // debug-panel only: fake happiness added on top of the real factors, so the
    // golden age / strike / riot tiers can be demoed without building 5 towns
    private int debugHappinessOffset = 0;

    public void addDebugHappiness(int delta) { debugHappinessOffset += delta; }

    public int getHappiness() {
        int happiness = debugHappinessOffset;
        for (Pair<Integer, Integer> hex : territory) {
            if (!Pair.isValid(hex)) continue;
            Improvement imp = Game.instance.map[hex.x][hex.y].getImprovement();
            if (imp == null) continue;
            if (imp.getImprovementType() == sut.civilization.Model.ModulEnums.ImprovementType.TOWN) happiness -= 1;
            if (imp.getImprovementType() == sut.civilization.Model.ModulEnums.ImprovementType.MONUMENT) happiness += 2;
        }
        if (units.size() >= getUnitCap()) happiness -= 1;
        if (getMilitaryUnitCount() >= getMilitaryUnitCap()) happiness -= 1;
        // spec (P2-25): the garrison bonus needs a MILITARY unit on the town hall
        // hex, a worker standing there doesn't make anyone feel safer
        if (townHall != null && townHall.getMainLand() != null
                && townHall.getMainLand().getCivilizedUnit() != null
                && townHall.getMainLand().getCivilizedUnit().getOwnerNation() == this
                && townHall.getMainLand().getCivilizedUnit().getCivilizedUnitType().isMilitary) {
            happiness += 1;
        }
        return happiness;
    }

    public boolean isInGoldenAge() { return getHappiness() >= 3; }
    public boolean isUnhappy()     { return getHappiness() <= -3; }
    public boolean isInRiot()      { return getHappiness() <= -5; }

    // last happiness tier we notified the player about (0 normal, 1 golden, -1 strike, -2 riot)
    private int lastHappinessTier = 0;
    public int getLastHappinessTier() { return lastHappinessTier; }
    public void setLastHappinessTier(int t) { lastHappinessTier = t; }
}

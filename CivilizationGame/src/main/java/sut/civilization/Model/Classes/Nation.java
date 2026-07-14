package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ResourceType;
import sut.civilization.Model.ModulEnums.TechnologyType;

import java.util.ArrayList;
import java.util.HashMap;

public class Nation {

    // ── Resources ─────────────────────────────────────────────────────────────
    private static final int INITIAL_CAPACITY = 200;
    private static final int INITIAL_FOOD  = 20;
    private static final int INITIAL_WOOD  = 15;
    private static final int INITIAL_STONE = 5;
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
    /** Hex coordinates that belong to this player's territory */
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

    /** Attempt to spend resources. Returns false (and spends nothing) if any amount is insufficient. */
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

    public int getUnitCap() { return baseUnitCap + extraUnitCap; }
    public int getUnitCount() { return units.size(); }
    public boolean canTrainUnit() { return units.size() < getUnitCap(); }
    public void addTownUnitCapBonus() { extraUnitCap += UNIT_CAP_PER_TOWN; }

    /** Sets the player-chosen initial Unit Cap (spec: chosen by the player at game start,
     *  not a fixed constant). Must be called before any Town bonuses are applied. */
    public void setBaseUnitCap(int baseUnitCap) { this.baseUnitCap = baseUnitCap; }

    // ── Starvation ────────────────────────────────────────────────────────────

    public boolean isInStarvation() { return inStarvation; }
    public void setInStarvation(boolean v) { this.inStarvation = v; }

    // ── Storage capacity (upgraded via Town Hall) ─────────────────────────────
    // Doc (Storage Upgrade 1 & 2): capacity increase applies to wood, stone, and iron only -
    // food is not listed and keeps its base capacity.

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
}

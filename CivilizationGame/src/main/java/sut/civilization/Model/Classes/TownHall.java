package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.ResourceType;
import sut.civilization.Model.ModulEnums.TechnologyType;

import java.util.ArrayDeque;
import java.util.Deque;

public class TownHall {

    private static final int SAFEGUARD_FOOD_PER_TURN = 1;
    private static final int SAFEGUARD_WOOD_PER_TURN = 1;
    private static final int STORAGE_UPGRADE_BONUS   = 100;
    private static final int GROWTH_THRESHOLD = 20;

    private final Nation ownerNation;
    private Land mainLand;
    private int level = 1;

    // City population: grows from banked food surplus, frozen during starvation.
    private int population = 1;
    private int growthProgress = 0;

    // Production queue - each entry is a unit type to train
    private final Deque<CivilizedUnitType> productionQueue = new ArrayDeque<>();
    private int turnsLeftForCurrent = 0;

    public TownHall(Nation ownerNation, Land mainLand) {
        this.ownerNation = ownerNation;
        this.mainLand = mainLand;
    }

    // ── Safeguard production ──────────────────────────────────────────────────
    // Called each turn before starvation check; always produces a baseline wood+food amount
    // so a miscalculating player is never left with zero resources.
    public void produceSafeguardTrickle() {
        ownerNation.getFood().add(SAFEGUARD_FOOD_PER_TURN);
        ownerNation.getFood().addToNetRate(SAFEGUARD_FOOD_PER_TURN);
        ownerNation.getWood().add(SAFEGUARD_WOOD_PER_TURN);
        ownerNation.getWood().addToNetRate(SAFEGUARD_WOOD_PER_TURN);
    }

    // ── Population growth ──────────────────────────────────────────────────────
    // Doc: during starvation, city population growth completely stops.
    // Growth accumulates from that turn's food surplus; nothing accrues while starving.
    public void tickPopulationGrowth(int foodNetRate) {
        if (ownerNation.isInStarvation()) return;
        growthProgress += Math.max(0, foodNetRate);
        if (growthProgress >= GROWTH_THRESHOLD) {
            growthProgress -= GROWTH_THRESHOLD;
            population++;
        }
    }

    public int getPopulation() { return population; }

    // ── Production queue ──────────────────────────────────────────────────────

    public boolean enqueueUnit(CivilizedUnitType type) {
        if (!ownerNation.canTrainUnit()) return false;
        if (ownerNation.getFood().getBalance() < type.productionCostFood) return false;
        ownerNation.getFood().spend(type.productionCostFood);
        productionQueue.addLast(type);
        if (productionQueue.size() == 1) turnsLeftForCurrent = type.turnsToTrain;
        return true;
    }

    /**
     * Called each game turn. Returns the trained unit type if one finished, else null.
     * The caller is responsible for placing the unit on the map.
     */
    public CivilizedUnitType advanceQueue() {
        if (productionQueue.isEmpty()) return null;
        turnsLeftForCurrent--;
        if (turnsLeftForCurrent <= 0) {
            CivilizedUnitType done = productionQueue.pollFirst();
            if (!productionQueue.isEmpty()) {
                turnsLeftForCurrent = productionQueue.peekFirst().turnsToTrain;
            }
            return done;
        }
        return null;
    }

    /** Puts a just-finished unit back at the front of the queue (spawn tile was occupied). */
    public void requeueFront(CivilizedUnitType type) {
        productionQueue.addFirst(type);
        turnsLeftForCurrent = 0; // already fully trained - try to spawn again next turn
    }

    public boolean hasQueuedProduction() { return !productionQueue.isEmpty(); }
    public CivilizedUnitType getCurrentProduction() { return productionQueue.peekFirst(); }
    public int getTurnsLeftForCurrent() { return turnsLeftForCurrent; }

    // ── Technology / upgrade ──────────────────────────────────────────────────

    public boolean researchTechnology(TechnologyType tech) {
        if (ownerNation.hasTechnology(tech)) return false;
        if (!tech.isAvailable(ownerNation.getTechnologies())) return false;
        if (!ownerNation.spend(0, tech.woodCost, tech.stoneCost, tech.ironCost)) return false;

        ownerNation.unlockTechnology(tech);

        if (tech == TechnologyType.STORAGE_UPGRADE_1 || tech == TechnologyType.STORAGE_UPGRADE_2) {
            ownerNation.upgradeStorageCapacity(STORAGE_UPGRADE_BONUS);
        }
        return true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public Land getMainLand() { return mainLand; }
    public void setMainLand(Land mainLand) { this.mainLand = mainLand; }
    public Nation getOwnerNation() { return ownerNation; }
}

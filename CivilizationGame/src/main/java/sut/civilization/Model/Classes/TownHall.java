package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.TechnologyType;

import java.util.ArrayDeque;
import java.util.Deque;

public class TownHall implements java.io.Serializable {

    private static final int SAFEGUARD_FOOD_PER_TURN = 1;
    private static final int SAFEGUARD_WOOD_PER_TURN = 1;
    private static final int STORAGE_UPGRADE_BONUS   = 100;
    private static final int GROWTH_THRESHOLD = 20;

    // ── Phase 2: levels & HP ──────────────────────────────────────────────────
    public static final int MAX_LEVEL = 3;
    private static final int BASE_MAX_HP = 200;
    private static final int MAX_HP_PER_LEVEL = 50;   // level 2 -> 250, level 3 -> 300
    private static final int UPGRADE_HEAL = 50;
    private static final int LEVEL_UP_STORAGE_BONUS = 50; // wood/stone/iron capacity per level-up

    // upgrade to level 2 (Settlement): 50 wood + 50 stone, 3 turns
    public static final int L2_WOOD = 50, L2_STONE = 50, L2_IRON = 0, L2_TURNS = 3;
    // upgrade to level 3 (Capital): 100 stone + 50 iron, 5 turns
    public static final int L3_WOOD = 0, L3_STONE = 100, L3_IRON = 50, L3_TURNS = 5;

    private final Nation ownerNation;
    private Land mainLand;
    private int level = 1;
    private int hp = BASE_MAX_HP;

    // population grows from banked food surplus, freezes while starving
    private int population = 1;
    private int growthProgress = 0;

    // Phase 2: unified production "queue" - per spec only ONE order can be active at
    // a time (train / research / upgrade share it); new orders are blocked while busy
    // unless the player cancels. Still a deque so a finished-but-unspawnable unit can
    // be put back at the front.
    private final Deque<ProductionOrder> productionQueue = new ArrayDeque<>();

    /** Per spec: the town hall works on one order at a time. */
    public boolean isBusy() { return !productionQueue.isEmpty(); }

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
    public void tickPopulationGrowth(int foodNetRate) {
        if (ownerNation.isInStarvation()) return;
        growthProgress += Math.max(0, foodNetRate);
        if (growthProgress >= GROWTH_THRESHOLD) {
            growthProgress -= GROWTH_THRESHOLD;
            population++;
        }
    }

    public int getPopulation() { return population; }

    // ── Production queue (Phase 2 unified) ────────────────────────────────────
    // Costs are paid on enqueue. Cancel does NOT refund (per spec).

    public boolean enqueueUnit(CivilizedUnitType type) {
        if (isBusy()) return false;
        if (!ownerNation.canTrainUnit()) return false;
        // phase 2 military units: TH level gate, separate military cap, wood cost
        if (level < type.requiredTownHallLevel) return false;
        if (type.isMilitary && !ownerNation.canTrainMilitaryUnit()) return false;
        if (ownerNation.getFood().getBalance() < type.productionCostFood) return false;
        if (ownerNation.getWood().getBalance() < type.productionCostWood) return false;
        ownerNation.getFood().spend(type.productionCostFood);
        ownerNation.getWood().spend(type.productionCostWood);
        productionQueue.addLast(ProductionOrder.trainUnit(type));
        return true;
    }

    public boolean enqueueResearch(TechnologyType tech) {
        if (isBusy()) return false;
        if (ownerNation.hasTechnology(tech)) return false;
        if (!tech.isAvailable(ownerNation.getTechnologies())) return false;
        if (level < tech.requiredTownHallLevel) return false;
        if (!ownerNation.spend(0, tech.woodCost, tech.stoneCost, tech.ironCost)) return false;
        productionQueue.addLast(ProductionOrder.research(tech));
        return true;
    }

    public boolean enqueueTownHallUpgrade() {
        if (isBusy()) return false;
        if (level >= MAX_LEVEL) return false;
        int target = level + 1;
        if (target == 2) {
            if (!ownerNation.spend(0, L2_WOOD, L2_STONE, L2_IRON)) return false;
            productionQueue.addLast(ProductionOrder.upgradeTownHall(2, L2_TURNS));
        } else {
            if (!ownerNation.spend(0, L3_WOOD, L3_STONE, L3_IRON)) return false;
            productionQueue.addLast(ProductionOrder.upgradeTownHall(3, L3_TURNS));
        }
        return true;
    }

    /** Cancels the order currently in progress. No refund (per spec). */
    public ProductionOrder cancelCurrent() {
        return productionQueue.pollFirst();
    }

    /**
     * Called each game turn. Ticks the order at the head of the queue and returns it
     * if it finished this turn (already removed from the queue), else null.
     * Research/upgrade effects are applied by the caller (GameController).
     */
    public ProductionOrder advanceQueue() {
        ProductionOrder current = productionQueue.peekFirst();
        if (current == null) return null;
        current.tick();
        if (current.isDone()) {
            productionQueue.pollFirst();
            return current;
        }
        return null;
    }

    /** Puts a just-finished unit order back at the front (spawn tile was occupied). */
    public void requeueFront(CivilizedUnitType type) {
        ProductionOrder order = ProductionOrder.trainUnit(type);
        order.setTurnsLeft(0); // already trained - just retry the spawn next turn
        productionQueue.addFirst(order);
    }

    public boolean hasQueuedProduction() { return !productionQueue.isEmpty(); }
    public ProductionOrder getCurrentProduction() { return productionQueue.peekFirst(); }
    public Deque<ProductionOrder> getProductionQueue() { return productionQueue; }

    public boolean isAlreadyQueued(TechnologyType tech) {
        for (ProductionOrder o : productionQueue)
            if (o.getKind() == ProductionOrder.Kind.RESEARCH_TECH && o.getTech() == tech) return true;
        return false;
    }

    public boolean isUpgradeQueued() {
        for (ProductionOrder o : productionQueue)
            if (o.getKind() == ProductionOrder.Kind.UPGRADE_TOWN_HALL) return true;
        return false;
    }

    // ── Order completion effects (called by GameController when an order finishes) ──

    public void applyResearch(TechnologyType tech) {
        ownerNation.unlockTechnology(tech);
        if (tech == TechnologyType.STORAGE_UPGRADE_1 || tech == TechnologyType.STORAGE_UPGRADE_2) {
            ownerNation.upgradeStorageCapacity(STORAGE_UPGRADE_BONUS);
        }
    }

    public void applyLevelUp(int targetLevel) {
        level = targetLevel;
        hp = Math.min(getMaxHp(), hp + UPGRADE_HEAL);
        // spec: each town hall level also raises storage capacity
        ownerNation.upgradeStorageCapacity(LEVEL_UP_STORAGE_BONUS);
    }

    // ── HP ────────────────────────────────────────────────────────────────────

    public int getMaxHp() { return BASE_MAX_HP + (level - 1) * MAX_HP_PER_LEVEL; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, Math.min(getMaxHp(), hp)); }

    /** Name of the current level shown in the UI. */
    public String getLevelName() {
        switch (level) {
            case 2: return "Settlement";
            case 3: return "Capital";
            default: return "Town Hall";
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public Land getMainLand() { return mainLand; }
    public void setMainLand(Land mainLand) { this.mainLand = mainLand; }
    public Nation getOwnerNation() { return ownerNation; }
}

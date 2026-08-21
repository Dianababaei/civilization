package sut.civilization.Model.Classes;

import sut.civilization.Model.ModulEnums.ImprovementType;
import sut.civilization.Model.ModulEnums.LandType;

import java.util.ArrayList;

public class Land implements java.io.Serializable {

    protected Improvement improvement;
    protected Ruin ruin;
    protected LandFeature landFeature;

    protected int cost;
    protected CivilizedUnit civilizedUnit = null;
    /** Phase 2 combat: hostile occupant (barbarian / bear / camp), max one per hex. */
    protected Enemy enemy = null;

    protected boolean isAPartOfPath = false;
    protected LandType landType;
    protected ArrayList<Nation> seerNations = new ArrayList<>();
    /** Nations that have revealed this hex specifically via an Explorer unit (or the TownHall's
     *  initial reveal). Border Expander requires this, per spec, not generic {@link #seerNations}
     *  which any unit type contributes to. */
    protected ArrayList<Nation> explorerSeenNations = new ArrayList<>();
    protected int visibility = 2;
    // 0 -> fog of war
    // 1 -> shadow (unknown)
    // 2 -> visible (shown)
    protected Resource resource;
    protected boolean hasIron = false;
    /** Phase 2: builder-built road. Moving between two road hexes costs only 1 AP. */
    protected boolean hasRoad = false;
    /** Phase 2: volcano hexes are a visually distinct mountain subtype set at map generation. */
    protected boolean isVolcano = false;
    /** Independent depletable iron deposit; only set on mountain tiles where hasIron is true. */
    protected Resource ironResource;
    //TODO assign land's movement cost it's landType movement cost when initializing map
    protected int movementCost;
    protected int i;
    protected int j;


    public Land(LandType landType, int cost, int i, int j) {
        this.landType = landType;
        this.cost = cost;
        this.landFeature = null;
        this.improvement = null;
        this.i = i;
        this.j = j;
        this.ruin = null;
        this.visibility = 0;
    }

    public ArrayList<Nation> getSeerNations() {
        if (seerNations == null) this.seerNations = new ArrayList<>();
        return seerNations;
    }

    public void addSeerNation(Nation seerNation) {
        if (!this.seerNations.contains(seerNation))
            this.seerNations.add(seerNation);
    }

    public ArrayList<Nation> getExplorerSeenNations() {
        if (explorerSeenNations == null) this.explorerSeenNations = new ArrayList<>();
        return explorerSeenNations;
    }

    public void addExplorerSeenNation(Nation nation) {
        if (!this.getExplorerSeenNations().contains(nation))
            this.getExplorerSeenNations().add(nation);
    }

    /** True if the given nation has ever explored this hex with an Explorer (or its TownHall's starting reveal). */
    public boolean isExploredBy(Nation nation) {
        return getExplorerSeenNations().contains(nation);
    }

    public CivilizedUnit getCivilizedUnit() {
        return civilizedUnit;
    }

    public Enemy getEnemy() {
        return enemy;
    }

    public void setEnemy(Enemy enemy) {
        this.enemy = enemy;
    }

    public int getMP() {
        // phase 2 seasons: winter makes every land hex cost 1 extra AP to enter
        int winterPenalty = sut.civilization.Model.ModulEnums.Season.forTurn(Game.instance.getTurn())
                == sut.civilization.Model.ModulEnums.Season.WINTER ? 1 : 0;
        return landType.movementCost + winterPenalty;
    }

    /**
     * Phase 2 roads: cost to enter this hex when coming from a specific hex.
     * If both hexes have a road the step costs just 1 (e.g. forest 2 -> 1);
     * the winter +1 still applies on top since it hits every land hex.
     */
    public int getMPFrom(Land from) {
        if (from != null && from.hasRoad() && this.hasRoad) {
            int winterPenalty = sut.civilization.Model.ModulEnums.Season.forTurn(Game.instance.getTurn())
                    == sut.civilization.Model.ModulEnums.Season.WINTER ? 1 : 0;
            return Math.min(getMP(), 1 + winterPenalty);
        }
        return getMP();
    }

    public boolean hasRoad() { return hasRoad; }
    public void setHasRoad(boolean hasRoad) { this.hasRoad = hasRoad; }

    public int getCost() {
        return cost;
    }

    public Improvement getImprovement() {
        return improvement;
    }

    public void setImprovement(Improvement improvement) {
        this.improvement = improvement;
    }

    public LandFeature getLandFeature() {
        return landFeature;
    }

    public void setLandFeature(LandFeature landFeature) {
        this.landFeature = landFeature;
    }

    public void setCivilizedUnit(CivilizedUnit civilizedUnit) {
        this.civilizedUnit = civilizedUnit;
    }

    public boolean isAPartOfPath() {
        return isAPartOfPath;
    }

    public LandType getLandType() {
        return landType;
    }

    public void setLandType(LandType landType) {
        this.landType = landType;
    }

    public int getVisibility() {
        return visibility;
    }

    public void setVisibility(int visibility) {
        this.visibility = visibility;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public void setAPartOfPath(boolean APartOfPath) {
        isAPartOfPath = APartOfPath;
    }

    public void setMovementCost(int movementCost) {
        this.movementCost = movementCost;
    }

    public int getI() {
        return i;
    }

    public int getJ() {
        return j;
    }

    public void setI(int i) {
        this.i = i;
    }

    public void setJ(int j) {
        this.j = j;
    }

    public Ruin getRuin() {
        return ruin;
    }

    public void setRuin(Ruin ruin) {
        this.ruin = ruin;
    }

    /** True if this tile has a FOOD resource deposit (wheat/rice or livestock). */
    public boolean hasFoodResource() {
        return resource != null && resource.getResourceType() == sut.civilization.Model.ModulEnums.ResourceType.FOOD;
    }

    /** True if this mountain tile also carries iron ore (independent of its guaranteed stone). */
    public boolean hasIron() {
        return hasIron;
    }

    /** True if this tile had a resource deposit that has since been fully extracted. */
    public boolean isResourceDepleted() {
        return resource != null && resource.isDepleted();
    }

    public void setHasIron(boolean hasIron) {
        this.hasIron = hasIron;
    }

    public boolean isVolcano() { return isVolcano; }
    public void setVolcano(boolean volcano) { isVolcano = volcano; }

    public Resource getIronResource() {
        return ironResource;
    }

    public void setIronResource(Resource ironResource) {
        this.ironResource = ironResource;
    }

    /** True if this tile's independent iron deposit has been fully extracted. */
    public boolean isIronDepleted() {
        return ironResource != null && ironResource.isDepleted();
    }
}

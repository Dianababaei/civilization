package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.regex.Matcher;

public class CheatController extends GameController {

    public String putUnit(Matcher matcher) {
        Pair<Integer, Integer> coord = new Pair<>(
                Integer.parseInt(matcher.group("x")),
                Integer.parseInt(matcher.group("y")));
        if (!Pair.isValid(coord)) return "Coordinate is not valid!";

        Land tile = Game.instance.map[coord.x][coord.y];
        if (tile.getCivilizedUnit() != null) return "A unit is already on this tile!";
        if (tile.getEnemy() != null) return "An enemy is on this tile!";

        // defaults to spawning an explorer
        CivilizedUnit unit = new CivilizedUnit(
                CivilizedUnitType.EXPLORER,
                currentTurnUser.getNation(), coord);
        tile.setCivilizedUnit(unit);
        currentTurnUser.getNation().addUnit(unit);
        return "Explorer spawned at (" + coord.x + "," + coord.y + ")";
    }

    public String putUnitByType(Matcher matcher, String typeName) {
        Pair<Integer, Integer> coord = new Pair<>(
                Integer.parseInt(matcher.group("x")),
                Integer.parseInt(matcher.group("y")));
        if (!Pair.isValid(coord)) return "Coordinate is not valid!";

        CivilizedUnitType type = null;
        for (CivilizedUnitType t : CivilizedUnitType.values())
            if (t.name.equalsIgnoreCase(typeName)) { type = t; break; }
        if (type == null) return "Unknown unit type: " + typeName + "!";

        Land tile = Game.instance.map[coord.x][coord.y];
        if (tile.getCivilizedUnit() != null) return "A unit is already on this tile!";
        if (tile.getEnemy() != null) return "An enemy is on this tile!";

        CivilizedUnit unit = new CivilizedUnit(type, currentTurnUser.getNation(), coord);
        tile.setCivilizedUnit(unit);
        currentTurnUser.getNation().addUnit(unit);
        return type.name + " spawned at (" + coord.x + "," + coord.y + ")";
    }

    public String addTechnology(Matcher matcher) {
        String name = matcher.group("name");
        for (TechnologyType t : TechnologyType.values()) {
            if (t.name.equalsIgnoreCase(name)) {
                currentTurnUser.getNation().unlockTechnology(t);
                return "Technology unlocked: " + t.name;
            }
        }
        return "Unknown technology: " + name + "!";
    }

    public String destroyImprovement(Matcher matcher) {
        Pair<Integer, Integer> loc = new Pair<>(
                Integer.parseInt(matcher.group("x")),
                Integer.parseInt(matcher.group("y")));
        Land tile = Game.instance.map[loc.x][loc.y];
        if (tile.getImprovement() == null) return "No improvement here!";
        tile.setImprovement(null);
        return "Improvement destroyed.";
    }

    public String addResource(Matcher matcher) {
        String res  = matcher.group("resource");
        int amount  = Integer.parseInt(matcher.group("amount"));
        Nation n    = currentTurnUser.getNation();
        switch (res.toLowerCase()) {
            case "food":  n.getFood().add(amount);  break;
            case "wood":  n.getWood().add(amount);  break;
            case "stone": n.getStone().add(amount); break;
            case "iron":  n.getIron().add(amount);  break;
            default: return "Unknown resource: " + res + ". Use food/wood/stone/iron!";
        }
        return amount + " " + res + " added.";
    }

    public String increaseTurn(Matcher matcher) {
        int amount = Integer.parseInt(matcher.group("amount"));
        for (int i = 0; i < amount; i++) nextGameTurn();
        return "Advanced " + amount + " turns.";
    }

    public String winGame() {
        return "You won";
    }

    // ── direct helpers for the in-game Debug panel (phase 2 testing/demo) ─────
    // the evaluators need a quick way to reach any feature without grinding turns

    public static String debugAddAllResources(int amount) {
        Nation n = currentTurnUser.getNation();
        n.getFood().add(amount);
        n.getWood().add(amount);
        n.getStone().add(amount);
        n.getIron().add(amount);
        return "+" + amount + " food/wood/stone/iron (capped at storage capacity).";
    }

    public static String debugAdvanceTurns(int amount) {
        for (int i = 0; i < amount; i++) nextGameTurn();
        return "Advanced " + amount + " turn(s). Now turn " + Game.instance.getTurn() + ".";
    }

    // spawns Workers on free non-mountain tiles until the nation hits its unit cap,
    // so the "at cap" behavior (Train blocked, happiness -1) can be demoed quickly
    public static String debugFillUnitsToCap() {
        Nation n = currentTurnUser.getNation();
        int cap = n.getUnitCap();
        int spawned = 0;

        // pass 1: own territory, pass 2: any discovered tile - never spawn into fog
        // so the demo units stay clustered around the base instead of littering the map
        for (int pass = 0; pass < 2 && n.getUnits().size() < cap; pass++) {
            for (int i = 0; i < Consts.MAP_SIZE.amount.x && n.getUnits().size() < cap; i++) {
                for (int j = 0; j < Consts.MAP_SIZE.amount.y && n.getUnits().size() < cap; j++) {
                    Land tile = Game.instance.map[i][j];
                    if (tile.getCivilizedUnit() != null || tile.getEnemy() != null) continue;
                    if (tile.getLandType() == LandType.MOUNTAIN || tile.getLandType() == LandType.MOUNTAIN_RANGE) continue;
                    if (tile.getVisibility() == 0) continue;
                    if (pass == 0 && !n.ownsHex(i, j)) continue;
                    // keep the TH hex empty - a garrisoned unit gives +1 happiness,
                    // which would hide the -1 "at cap" effect this button demos
                    if (n.getTownHall() != null && n.getTownHall().getMainLand() == tile) continue;
                    CivilizedUnit unit = new CivilizedUnit(CivilizedUnitType.WORKER, n, new Pair<>(i, j));
                    tile.setCivilizedUnit(unit);
                    n.addUnit(unit);
                    spawned++;
                }
            }
        }
        if (n.getUnits().size() >= cap)
            return "Spawned " + spawned + " Workers. Units now " + n.getUnits().size() + "/" + cap + " (AT CAP).";
        return "Spawned " + spawned + " Workers but ran out of free tiles (" + n.getUnits().size() + "/" + cap + ")!";
    }

    public static String debugAdjustHappiness(int delta) {
        Nation n = currentTurnUser.getNation();
        n.addDebugHappiness(delta);
        int now = n.getHappiness();
        String tier = now >= 3 ? " (GOLDEN AGE)" : now <= -5 ? " (RIOT)" : now <= -3 ? " (STRIKE)" : "";
        return "Happiness " + (delta > 0 ? "+" : "") + delta + " -> now " + now + tier + ".";
    }

    // fires an earthquake centered on a random hex the player can currently see,
    // so the demo shake/flash is guaranteed to happen on-screen
    public static String debugTriggerEarthquake() {
        java.util.ArrayList<Pair<Integer, Integer>> visible = new java.util.ArrayList<>();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                if (Game.instance.map[i][j].getVisibility() == 2) visible.add(new Pair<>(i, j));
        if (visible.isEmpty()) return "No visible hex to strike!";
        Pair<Integer, Integer> center = visible.get(Game.instance.getRng().nextInt(visible.size()));
        return DisasterController.triggerEarthquake(center);
    }

    // spawns an enemy (Barbarian / Bear / Camp) on the nearest free visible tile to the
    // Town Hall, so a combat demo starts right at home instead of across the map
    public static String debugSpawnEnemy(String kind) {
        EnemyType type = null;
        for (EnemyType t : EnemyType.values())
            if (t.name().equalsIgnoreCase(kind) || t.name.equalsIgnoreCase(kind)) { type = t; break; }
        if (type == null) return "Unknown enemy type: " + kind + "!";

        TownHall th = currentTurnUser.getNation().getTownHall();
        Pair<Integer, Integer> center = th != null
                ? new Pair<>(th.getMainLand().getI(), th.getMainLand().getJ())
                : new Pair<>(7, 7);
        for (int radius = 1; radius <= 6; radius++) {
            for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(center, radius)) {
                if (!Pair.isValid(hex) || hex.equals(center)) continue;
                Land tile = Game.instance.map[hex.x][hex.y];
                if (tile.getLandType() == LandType.MOUNTAIN || tile.getLandType() == LandType.MOUNTAIN_RANGE) continue;
                if (tile.getCivilizedUnit() != null || tile.getEnemy() != null || tile.getImprovement() != null) continue;
                if (tile.getVisibility() != 2) continue;
                tile.setEnemy(new Enemy(type, hex));
                return type.name + " spawned at (" + hex.x + "," + hex.y + ")"
                        + (type == EnemyType.CAMP ? " - kill its guard first, then hit the structure." : ".");
            }
        }
        return "No free visible tile near the Town Hall to spawn on!";
    }

    // reveals the whole map for the current player (marks tiles as explorer-seen too,
    // so Border Expanders can claim anywhere) - for finding Trading Posts / volcanoes fast
    public static String debugRevealMap() {
        Nation n = currentTurnUser.getNation();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                tile.setVisibility(2);
                tile.addSeerNation(n);
                tile.addExplorerSeenNation(n);
            }
        }
        return "Whole map revealed. Look for the 2 Trading Post huts and the volcanoes.";
    }
}

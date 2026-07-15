package sut.civilization.Controller.GameControllers;

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
        if (type == null) return "Unknown unit type: " + typeName;

        Land tile = Game.instance.map[coord.x][coord.y];
        if (tile.getCivilizedUnit() != null) return "A unit is already on this tile!";

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
        return "Unknown technology: " + name;
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
            default: return "Unknown resource: " + res + ". Use food/wood/stone/iron.";
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
}

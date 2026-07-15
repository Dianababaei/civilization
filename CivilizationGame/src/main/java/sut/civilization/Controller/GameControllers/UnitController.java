package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

public class UnitController extends GameController {

    // these flags get set inside unitGoForward() so the GUI can react after each step.
    // gets checked (and reset) by GamePlayController every step of the animation

    // true if this step landed on a ruin, so we know to show the "ruin retrieved" popup
    public static boolean lastStepRetrievedRuin = false;

    // true if the unit couldn't finish its path because the destination got occupied
    public static boolean lastStepBlocked = false;

    // which tile was occupied and blocked us, so we can show it in the popup message
    public static Pair<Integer, Integer> lastBlockedHex = null;

    /**
     * Validates and queues a path for the selected unit; does NOT move it.
     * The caller (GamePlayController) animates the walk hex-by-hex via unitGoForward(),
     * since the doc requires visible step-by-step movement, not an instant teleport.
     */
    public static String unitSetPath(int x, int y, int selection) {
        Unit selectedUnit = selectedCivilizedUnit;
        if (selectedUnit != null && selectedUnit.getUnitStatus() == UnitStatus.WORKING)
            return "The unit is busy now!";
        if (selectedUnit instanceof CivilizedUnit && ((CivilizedUnit) selectedUnit).isStationed())
            return "Unstation the Worker before moving it!";
        Land origin = Game.instance.map[selectedUnit.getLocation().x][selectedUnit.getLocation().y];
        Land dest = Game.instance.map[x][y];
        int originNum = LandController.getLandNumber(origin);
        int destNum = LandController.getLandNumber(dest);
        if (dest.getCivilizedUnit() != null)
            return "There already is a civilized unit in destination";
        if (Game.instance.dist[originNum][destNum] < 1000) {
            selectedUnit.setPath(Game.instance.path[originNum][destNum]);
        } else
            return "Unit can't reach there";
        return "Unit moving...";
    }

    public static boolean hasMoreSteps(Unit unit) {
        return unit.getMP() > 0 && unit.getPath() != null && !unit.getPath().equals("");
    }

    public static void unitGoForward(Unit unit) {
        String path = unit.getPath();
        int neighbor = 0;

        if (path != null && !path.equals(""))
            neighbor = Integer.parseInt(String.valueOf(path.charAt(0)));
        else {
            System.out.println("path is empty");
            return;
        }

        Pair<Integer, Integer> next = LandController.getNeighborIndex(unit.getLocation(), neighbor);

        // units are allowed to pass by/through each other while moving, we only care
        // that the FINAL tile of the path is free. unitSetPath() already checked the
        // destination was empty when we queued the path, but something could have moved
        // there since then, so we double check only on the last step
        boolean isFinalStep = path.length() <= 1;
        CivilizedUnit occupant = Game.instance.map[next.x][next.y].getCivilizedUnit();
        if (isFinalStep && occupant != null) {
            unit.setPath("");
            unit.setUnitStatus(UnitStatus.AWAKE);
            lastStepBlocked = true;
            lastBlockedHex = next;
            return;
        }

        Land originTile = Game.instance.map[unit.getLocation().x][unit.getLocation().y];
        if (originTile.getCivilizedUnit() == unit) originTile.setCivilizedUnit(null);

        // only set the unit on the new tile if that tile is actually empty. if we did this
        // unconditionally we'd overwrite whatever unit is already standing there (like a
        // worker just parked mid-path) and it would basically disappear from the map -
        // this is the bug we had before where units vanished while walking past each other
        if (occupant == null) {
            Game.instance.map[next.x][next.y].setCivilizedUnit((CivilizedUnit) unit);
        }
        unit.setLocation(next);

        if (Game.instance.map[next.x][next.y].getRuin() != null) {
            unitRetrieveRuin(unit);
            lastStepRetrievedRuin = true;
        }

        unit.setMP(Math.max(0, unit.getMP() - Game.instance.map[next.x][next.y].getMP()));
        if (Game.instance.map[next.x][next.y].getLandFeature() != null)
            unit.setMP(Math.max(0, unit.getMP() - Game.instance.map[next.x][next.y].getLandFeature().getLandFeatureType().movementCost));
        unit.setPath(path.substring(1));
        unit.setUnitStatus(UnitStatus.MOVING);
    }

    public static String unitSleep() {
        Unit unit = selectedCivilizedUnit;
        if (unit != null) {
            if (unit.getUnitStatus() != UnitStatus.WORKING && unit.getUnitStatus() != UnitStatus.SLEEP) {
                unit.setUnitStatus(UnitStatus.SLEEP);
                return "Slept successfully.";
            } else return "can't sleep!";
        }
        return "Select a unit first!";
    }

    public void unitGarrison() { }

    public void unitCancelMission() { }

    public static String unitWake() {
        Unit unit = selectedCivilizedUnit;
        if (unit != null) {
            if (unit.getUnitStatus() == UnitStatus.SLEEP) {
                unit.setUnitStatus(UnitStatus.AWAKE);
                return "Waked successfully.";
            }
            return "This unit isn't asleep!";
        }
        return "Select a unit first!";
    }

    public static void unitRetrieveRuin(Unit unit) {
        Land land = Game.instance.map[unit.getLocation().x][unit.getLocation().y];
        Ruin ruin = land.getRuin();
        ruin.retrieve();
        land.setRuin(null);
    }
}

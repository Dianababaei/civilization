import sut.civilization.Controller.GameControllers.*;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;

public class QaDriver {
    static int pass = 0, fail = 0;
    static void check(String label, boolean condition) {
        if (condition) { pass++; System.out.println("PASS: " + label); }
        else { fail++; System.out.println("FAIL: " + label); }
    }

    public static void main(String[] args) {
        User user = new User("player", "", "Player");
        Game.instance.setLoggedInUser(user);
        Game.instance.getPlayersInGame().clear();
        user.setNation(new Nation());
        Game.instance.getPlayersInGame().add(user);
        GameController.setCurrentTurnUser(user);
        Game.instance.setMap(LandController.mapInitializer());
        LandController.initializeDistances();
        LandController.updateDistances();
        Nation nation = user.getNation();

        System.out.println("=== End Turn: engine no longer hard-blocks on idle units ===");
        check("hasIdleUnits() true at game start (all units unordered)", GameController.hasIdleUnits());
        int turnBefore = Game.instance.getTurn();
        String msg = GameController.endTurn();
        check("endTurn() succeeds even with idle units (UI layer owns the warning now)", msg.startsWith("next turn:"));
        check("Turn actually advanced", Game.instance.getTurn() == turnBefore + 1);

        System.out.println("\n=== End Turn: still works normally when no idle units ===");
        for (Unit u : new ArrayList<>(nation.getUnits())) u.setCurrentAp(0);
        check("hasIdleUnits() false once all units are spent", !GameController.hasIdleUnits());
        String msg2 = GameController.endTurn();
        check("endTurn() succeeds with no idle units", msg2.startsWith("next turn:"));

        System.out.println("\n=== River mechanic fully removed - move cost is pure terrain-based now ===");
        CivilizedUnit explorer = (CivilizedUnit) nation.getUnits().stream()
                .filter(u -> ((CivilizedUnit)u).getCivilizedUnitType() == CivilizedUnitType.EXPLORER)
                .findFirst().get();
        explorer.setCurrentAp(4);
        GameController.setSelectedCivilizedUnit(explorer);
        Pair<Integer,Integer> startLoc = explorer.getLocation();
        Pair<Integer,Integer> dest = null;
        int destCost = -1;
        for (int dir = 0; dir < 6; dir++) {
            Pair<Integer,Integer> cand = LandController.getNeighborIndex(startLoc, dir);
            if (Pair.isValid(cand) && Game.instance.map[cand.x][cand.y].getCivilizedUnit() == null) {
                dest = cand;
                destCost = Game.instance.map[cand.x][cand.y].getMP();
                break;
            }
        }
        if (dest != null) {
            UnitController.unitSetPath(dest.x, dest.y, 0);
            int stepsRun = 0;
            while (UnitController.hasMoreSteps(explorer) && stepsRun < 20) { UnitController.unitGoForward(explorer); stepsRun++; }
            check("Explorer moved exactly one hex (single step path)", stepsRun == 1);
            check("AP after one-hex move equals 4 minus that tile's terrain cost (no river zeroing)",
                    explorer.getCurrentAp() == Math.max(0, 4 - destCost));
        }

        System.out.println("\n=== SUMMARY: " + pass + " passed, " + fail + " failed ===");
    }
}

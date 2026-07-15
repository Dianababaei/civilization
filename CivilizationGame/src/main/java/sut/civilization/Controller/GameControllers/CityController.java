package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;

import static sut.civilization.Model.Classes.Pair.isValid;

// city/settler stuff got removed, town hall gets placed by the builder unit now.
// kept isCityBuildable around renamed to isTownHallBuildable for the placement check
public class CityController extends GameController {

    public static boolean isTownHallBuildable(Pair<Integer, Integer> main) {
        if (!isValid(main)) return false;
        if (!Game.instance.map[main.x][main.y].getLandType().isWalkable) return false;

        Pair<Integer, Integer>[] neighbors = new Pair[6];
        for (int i = 0; i < 6; i++)
            neighbors[i] = LandController.getNeighborIndex(main, i);

        for (int i = 0; i < 6; i++) {
            if (isValid(neighbors[i])) {
                Pair<Integer, Integer>[] neighbors2 = new Pair[6];
                for (int j = 0; j < 6; j++)
                    neighbors2[j] = LandController.getNeighborIndex(neighbors[i], j);
                for (int j = 0; j < 6; j++) {
                    if (isValid(neighbors2[j])) {
                        Land tile = Game.instance.map[neighbors2[j].x][neighbors2[j].y];
                        if (tile.getImprovement() != null &&
                                tile.getImprovement().getImprovementType() ==
                                        sut.civilization.Model.ModulEnums.ImprovementType.TOWN)
                            return false;
                    }
                }
            }
        }
        return true;
    }

    // called from GameController.nextGameTurn() but doesn't do anything now, affordable
    // land tracking was a city thing that got removed. just kept as a stub so it still compiles
    public static void updateAffordableLands() { }
}

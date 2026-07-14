package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;

import static sut.civilization.Model.Classes.Pair.isValid;

// City/settler concepts are removed. TownHall is placed by the Builder unit.
// isCityBuildable is kept as isTownHallBuildable for Builder placement validation.
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

    // Called by GameController.nextGameTurn() - now a no-op since affordable
    // land tracking was a City concept. Kept as stub so call sites compile.
    public static void updateAffordableLands() { }
}

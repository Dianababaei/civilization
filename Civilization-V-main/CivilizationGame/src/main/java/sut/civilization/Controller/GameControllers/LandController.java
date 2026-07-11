package sut.civilization.Controller.GameControllers;

import sut.civilization.Controller.Controller;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.*;

import java.util.ArrayList;
import java.util.Random;

import sut.civilization.Enums.Consts;

public class LandController extends Controller {

    public static ArrayList<Pair<Integer, Integer>> getAllNeighborsIndexes(Pair<Integer, Integer> coordinate) {
        ArrayList<Pair<Integer, Integer>> neighborPairs = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            neighborPairs.add(getNeighborIndex(new Pair<>(coordinate.x, coordinate.y), i));
        return neighborPairs;
    }

    public static void updateLandVisibility() {
        Nation currentNation = GameController.getCurrentTurnUser().getNation();

        // Reset: fog by default; restore shadow for tiles this nation has already explored
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                tile.setVisibility(tile.getSeerNations().contains(currentNation) ? 1 : 0);
            }
        }

        // Reveal tiles within each unit's vision radius.
        // Delegates to GameController.revealAround so there is one BFS implementation
        // shared between the per-turn fog update and this per-render UI refresh.
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                int radius = 0;
                boolean isExplorer = false;
                if (tile.getCivilizedUnit() != null
                        && tile.getCivilizedUnit().getOwnerNation() == currentNation) {
                    CivilizedUnitType type = ((CivilizedUnit) tile.getCivilizedUnit()).getCivilizedUnitType();
                    radius = type.visionRadius;
                    isExplorer = type == CivilizedUnitType.EXPLORER;
                }
                if (radius > 0) {
                    GameController.revealAround(new Pair<>(i, j), radius, currentNation, isExplorer);
                }
            }
        }
        // Also reveal from TownHall (counts as "explored" for Border Expander purposes,
        // since this is the player's known starting area, not a transient unit passing by)
        if (currentNation.getTownHall() != null) {
            Land thLand = currentNation.getTownHall().getMainLand();
            if (thLand != null) {
                GameController.revealAround(new Pair<>(thLand.getI(), thLand.getJ()), 2, currentNation, true);
            }
        }
    }

    private static void lightNeighbors(Pair<Integer, Integer> coordinate) {
        for (Pair<Integer, Integer> pair : getAllNeighborsIndexes(coordinate)) {
            if (Pair.isValid(pair) && Game.instance.map[pair.x][pair.y].getLandType() != LandType.MOUNTAIN) {
                Game.instance.map[pair.x][pair.y].setVisibility(2);
                Game.instance.map[pair.x][pair.y].addSeerNation(GameController.getCurrentTurnUser().getNation());
            }
        }
    }

    public static Pair<Integer, Integer> getNeighborIndex(Pair<Integer, Integer> coordinate, int position) {
        if (coordinate.y % 2 == 0) {
            if (coordinate.x % 2 == 0) {
                switch (position) {
                    case 0: return new Pair<>(coordinate.x, coordinate.y - 1);
                    case 1: return new Pair<>(coordinate.x + 1, coordinate.y - 1);
                    case 2: return new Pair<>(coordinate.x + 1, coordinate.y);
                    case 3: return new Pair<>(coordinate.x, coordinate.y + 1);
                    case 4: return new Pair<>(coordinate.x - 1, coordinate.y);
                    case 5: return new Pair<>(coordinate.x - 1, coordinate.y - 1);
                }
            } else {
                switch (position) {
                    case 0: return new Pair<>(coordinate.x, coordinate.y - 1);
                    case 1: return new Pair<>(coordinate.x + 1, coordinate.y);
                    case 2: return new Pair<>(coordinate.x + 1, coordinate.y + 1);
                    case 3: return new Pair<>(coordinate.x, coordinate.y + 1);
                    case 4: return new Pair<>(coordinate.x - 1, coordinate.y + 1);
                    case 5: return new Pair<>(coordinate.x - 1, coordinate.y);
                }
            }
        } else {
            if (coordinate.x % 2 == 0) {
                switch (position) {
                    case 0: return new Pair<>(coordinate.x, coordinate.y - 1);
                    case 1: return new Pair<>(coordinate.x + 1, coordinate.y - 1);
                    case 2: return new Pair<>(coordinate.x + 1, coordinate.y);
                    case 3: return new Pair<>(coordinate.x, coordinate.y + 1);
                    case 4: return new Pair<>(coordinate.x - 1, coordinate.y);
                    case 5: return new Pair<>(coordinate.x - 1, coordinate.y - 1);
                }
            } else {
                switch (position) {
                    case 0: return new Pair<>(coordinate.x, coordinate.y - 1);
                    case 1: return new Pair<>(coordinate.x + 1, coordinate.y);
                    case 2: return new Pair<>(coordinate.x + 1, coordinate.y + 1);
                    case 3: return new Pair<>(coordinate.x, coordinate.y + 1);
                    case 4: return new Pair<>(coordinate.x - 1, coordinate.y + 1);
                    case 5: return new Pair<>(coordinate.x - 1, coordinate.y);
                }
            }
        }
        return null;
    }

    public static boolean areNeighbors(Pair<Integer, Integer> land1, Pair<Integer, Integer> land2) {
        for (int i = 0; i < 6; i++) {
            if (land2.equals(getNeighborIndex(land1, i)))
                return true;
        }
        return false;
    }

    public static int getIndex(Pair<Integer, Integer> land1, Pair<Integer, Integer> land2) {
        for (int i = 0; i < 6; i++) {
            if (land2.equals(getNeighborIndex(land1, i)))
                return i;
        }
        return -1;
    }

    public static Land[][] mapInitializer() {
        Land[][] map = new Land[Consts.MAP_SIZE.amount.x][Consts.MAP_SIZE.amount.y];
        Random random = new Random(Double.doubleToLongBits(Math.random()));
        LandType[] walkable = { LandType.FOREST, LandType.PLAIN, LandType.MEADOW };

        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                LandType landtype;
                switch (random.nextInt(4)) {
                    case 0:  landtype = LandType.FOREST;   break;
                    case 1:  landtype = LandType.PLAIN;    break;
                    case 2:  landtype = LandType.MOUNTAIN; break;
                    default: landtype = LandType.MEADOW;   break;
                }
                map[i][j] = new Land(landtype, random.nextInt(50) + 50, i, j);
            }
        }

        // Assign tile resources per spec: every FOREST hex has wood, every MOUNTAIN hex has
        // stone (with iron as an independent rarer subset), and only some MEADOW/PLAIN hexes
        // have a food deposit (wheat/rice or livestock).
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                LandType lt = map[i][j].getLandType();
                if (lt == LandType.FOREST) {
                    map[i][j].setResource(new Resource(ResourceType.WOOD));
                } else if (lt == LandType.MOUNTAIN) {
                    map[i][j].setResource(new Resource(ResourceType.STONE));
                    boolean hasIron = random.nextInt(3) == 0;
                    map[i][j].setHasIron(hasIron);
                    // Iron is an independent depletable deposit from the guaranteed stone,
                    // so mining out the stone doesn't affect the iron and vice versa.
                    if (hasIron) map[i][j].setIronResource(new Resource(ResourceType.IRON));
                } else if ((lt == LandType.MEADOW || lt == LandType.PLAIN) && random.nextInt(3) == 0) {
                    map[i][j].setResource(new Resource(ResourceType.FOOD));
                }
            }
        }

        // Spawn starting units for player 0 (single-player).
        // Doc: 1 Explorer + 2 Builders + 2 Workers at game start.
        Nation player0Nation = Game.instance.getPlayersInGame().get(0).getNation();

        // Place TownHall near the center of the 15×15 map
        Land townHallLand = map[7][7];
        TownHall townHall = new TownHall(player0Nation, townHallLand);
        player0Nation.setTownHall(townHall);
        // Mark TownHall tile and its neighbors as player territory
        Pair<Integer, Integer> thCoord = new Pair<>(7, 7);
        player0Nation.addToTerritory(thCoord);
        for (Pair<Integer, Integer> neighbor : getAllNeighborsIndexes(thCoord)) {
            if (Pair.isValid(neighbor)) player0Nation.addToTerritory(neighbor);
        }
        // Make TownHall tile and its immediate neighbors visible at start (per spec).
        // Also counts as "explored" for Border Expander purposes (it's the player's known
        // starting territory, not something a passing Builder/Worker happened to see).
        map[thCoord.x][thCoord.y].setVisibility(2);
        map[thCoord.x][thCoord.y].addSeerNation(player0Nation);
        map[thCoord.x][thCoord.y].addExplorerSeenNation(player0Nation);
        for (Pair<Integer, Integer> nb : getAllNeighborsIndexes(thCoord)) {
            if (Pair.isValid(nb)) {
                map[nb.x][nb.y].setVisibility(2);
                map[nb.x][nb.y].addSeerNation(player0Nation);
                map[nb.x][nb.y].addExplorerSeenNation(player0Nation);
            }
        }

        // Units on tiles adjacent to TownHall (avoid TownHall tile itself)
        int[][] startSlots = { {6,6}, {6,7}, {7,6}, {7,8}, {8,7} };
        CivilizedUnitType[] startTypes = {
            CivilizedUnitType.EXPLORER,
            CivilizedUnitType.BUILDER,
            CivilizedUnitType.BUILDER,
            CivilizedUnitType.WORKER,
            CivilizedUnitType.WORKER
        };
        for (int s = 0; s < startTypes.length; s++) {
            Pair<Integer, Integer> loc = new Pair<>(startSlots[s][0], startSlots[s][1]);
            // Avoid overlapping with existing civilized unit or out-of-bounds
            if (!Pair.isValid(loc) || map[loc.x][loc.y].getCivilizedUnit() != null) continue;
            CivilizedUnit unit = new CivilizedUnit(startTypes[s], player0Nation, loc);
            map[loc.x][loc.y].setCivilizedUnit(unit);
            player0Nation.getUnits().add(unit);
        }

        for (int i1 = 0; i1 < Consts.MAP_SIZE.amount.x; i1++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land land = map[i1][j];
                int chance = new Random().nextInt(10);
                if (chance <= 1 && land.getLandType() != LandType.MOUNTAIN && land.getCivilizedUnit() == null)
                    land.setRuin(new Ruin());
            }
        }

        return map;
    }

    public static Land getLandByCoordinates(int x, int y) {
        return Game.instance.map[x][y];
    }

    public static int getLandNumber(Land land) {
        int num = -1;
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                if (Game.instance.map[i][j].equals(land))
                    num = i * Consts.MAP_SIZE.amount.x + j;
            }
        }
        return num;
    }

    public static void initializeDistances() {
        for (int i1 = 0; i1 < Consts.MAP_SIZE.amount.x; i1++) {
            for (int j1 = 0; j1 < Consts.MAP_SIZE.amount.y; j1++) {
                for (int i2 = 0; i2 < Consts.MAP_SIZE.amount.x; i2++) {
                    for (int j2 = 0; j2 < Consts.MAP_SIZE.amount.y; j2++) {
                        Land land1 = getLandByCoordinates(i1, j1);
                        Land land2 = getLandByCoordinates(i2, j2);
                        int num1 = getLandNumber(land1);
                        int num2 = getLandNumber(land2);
                        if (num1 == num2) {
                            Game.instance.dist[num1][num2] = 0;
                            Game.instance.path[num1][num2] = "";
                        } else if (areNeighbors(new Pair<>(i1, j1), new Pair<>(i2, j2))) {
                            Game.instance.dist[num1][num2] = land2.getMP();
                            if (land2.getLandFeature() != null)
                                Game.instance.dist[num1][num2] += land2.getLandFeature().getLandFeatureType().movementCost;
                            Game.instance.path[num1][num2] = "" + getIndex(new Pair<>(i1, j1), new Pair<>(i2, j2));
                        } else {
                            Game.instance.dist[num1][num2] = 1000;
                            Game.instance.path[num1][num2] = "";
                        }
                    }
                }
            }
        }
    }

    public static void updateDistances() {
        for (int i1 = 0; i1 < Consts.MAP_SIZE.amount.x; i1++) {
            for (int j1 = 0; j1 < Consts.MAP_SIZE.amount.y; j1++) {
                for (int i2 = 0; i2 < Consts.MAP_SIZE.amount.x; i2++) {
                    for (int j2 = 0; j2 < Consts.MAP_SIZE.amount.y; j2++) {
                        for (int i3 = 0; i3 < Consts.MAP_SIZE.amount.x; i3++) {
                            for (int j3 = 0; j3 < Consts.MAP_SIZE.amount.y; j3++) {
                                int num1 = getLandNumber(getLandByCoordinates(i1, j1));
                                int num2 = getLandNumber(getLandByCoordinates(i2, j2));
                                int num3 = getLandNumber(getLandByCoordinates(i3, j3));
                                if (Game.instance.dist[num1][num2] + Game.instance.dist[num2][num3] < Game.instance.dist[num1][num3]) {
                                    Game.instance.dist[num1][num3] = Game.instance.dist[num1][num2] + Game.instance.dist[num2][num3];
                                    Game.instance.path[num1][num3] = Game.instance.path[num1][num2] + Game.instance.path[num2][num3];
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

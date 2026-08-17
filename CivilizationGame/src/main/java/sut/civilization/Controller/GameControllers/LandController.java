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

        // reset everything to fog first, then bring back shadow for tiles we've already seen
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = Game.instance.map[i][j];
                tile.setVisibility(tile.getSeerNations().contains(currentNation) ? 1 : 0);
            }
        }

        // reveal tiles in range of each unit's vision. calls GameController.revealAround so
        // we don't duplicate the BFS logic between here (UI refresh) and the per-turn update
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
        // also reveal around the town hall itself (counts as explored for border expander,
        // since it's the player's home base not just some unit passing through)
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

        // give tiles their resources: forest always has wood, mountain always has stone
        // (iron is a separate rarer thing on top), meadow/plain only sometimes get food
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                LandType lt = map[i][j].getLandType();
                if (lt == LandType.FOREST) {
                    map[i][j].setResource(new Resource(ResourceType.WOOD));
                } else if (lt == LandType.MOUNTAIN) {
                    map[i][j].setResource(new Resource(ResourceType.STONE));
                    boolean hasIron = random.nextInt(3) == 0;
                    map[i][j].setHasIron(hasIron);
                    // iron is tracked as its own separate deposit from stone, so mining out
                    // the stone doesn't touch iron and vice versa
                    if (hasIron) map[i][j].setIronResource(new Resource(ResourceType.IRON));
                } else if ((lt == LandType.MEADOW || lt == LandType.PLAIN) && random.nextInt(3) == 0) {
                    map[i][j].setResource(new Resource(ResourceType.FOOD));
                }
            }
        }

        // spawn starting units for player 0 (this is single-player only for now)
        // start is 1 explorer + 2 builders + 2 workers
        Nation player0Nation = Game.instance.getPlayersInGame().get(0).getNation();

        // town hall goes near the middle of the 15x15 map
        Land townHallLand = map[7][7];
        TownHall townHall = new TownHall(player0Nation, townHallLand);
        player0Nation.setTownHall(townHall);
        // town hall tile + its neighbors count as player territory
        Pair<Integer, Integer> thCoord = new Pair<>(7, 7);
        player0Nation.addToTerritory(thCoord);
        for (Pair<Integer, Integer> neighbor : getAllNeighborsIndexes(thCoord)) {
            if (Pair.isValid(neighbor)) player0Nation.addToTerritory(neighbor);
        }

        // random terrain can roll a starting area with zero food tiles, which would make
        // farm/stable impossible to build near the capital. force at least one neighbor
        // into each type (without touching the town hall tile itself)
        ensureStartingFoodTiles(map, thCoord);
        // same deal for lumber mill - make sure at least one neighbor is forest
        ensureStartingForestTile(map, thCoord);
        // town hall + neighbors are visible right from the start.
        // also counts as "explored" for border expander (it's the player's known starting
        // area, not something a unit happened to walk past)
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

        // put starting units on tiles next to town hall (not on the town hall tile itself)
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
            // skip if out of bounds or something's already there
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

        // phase 2: pre-place a couple of neutral Trading Posts at map generation.
        // they trade at 80% but only once they end up inside your territory. never
        // right next to the town hall - the player should have to expand to reach one
        placeTradingPosts(map, thCoord, random);

        // phase 2: mark ~2 mountains as volcanoes (visually distinct subtype; only
        // these will be able to erupt once the disaster system lands)
        int volcanoes = 0, vAttempts = 0;
        while (volcanoes < 2 && vAttempts < 200) {
            vAttempts++;
            int i = random.nextInt(Consts.MAP_SIZE.amount.x);
            int j = random.nextInt(Consts.MAP_SIZE.amount.y);
            if (map[i][j].getLandType() != LandType.MOUNTAIN || map[i][j].isVolcano()) continue;
            map[i][j].setVolcano(true);
            volcanoes++;
        }

        return map;
    }

    // drops 2 neutral trading posts on random empty non-mountain tiles, 3-6 hexes
    // away from the starting town hall (close enough to actually find and reach,
    // but never right next to it). they start under normal fog like everything else
    private static void placeTradingPosts(Land[][] map, Pair<Integer, Integer> thCoord, Random random) {
        int placed = 0, attempts = 0;
        while (placed < 2 && attempts < 300) {
            attempts++;
            int i = random.nextInt(Consts.MAP_SIZE.amount.x);
            int j = random.nextInt(Consts.MAP_SIZE.amount.y);
            Land tile = map[i][j];
            if (tile.getLandType() == LandType.MOUNTAIN) continue;
            if (tile.getImprovement() != null || tile.getRuin() != null) continue;
            int dist = Math.abs(i - thCoord.x) + Math.abs(j - thCoord.y);
            if (dist < 3 || dist > 6) continue;
            tile.setImprovement(new Improvement(ImprovementType.TRADING_POST));
            placed++;
        }
    }

    // makes sure the town hall's neighbor ring has at least one farm tile (meadow+food) and
    // one stable tile (plain+food). converts up to two neighbors if the random roll didn't
    // give us any (never touches the town hall tile itself). without this you could start
    // with literally no food tile nearby
    private static void ensureStartingFoodTiles(Land[][] map, Pair<Integer, Integer> thCoord) {
        ArrayList<Pair<Integer, Integer>> neighbors = getAllNeighborsIndexes(thCoord);
        // tiles we shouldn't touch in the fallback below - either we already converted them
        // this call, or they already naturally satisfy meadow+food/plain+food (don't want to
        // steal one to fix the other, that just moves the shortage around)
        ArrayList<Pair<Integer, Integer>> reserved = new ArrayList<>();
        for (Pair<Integer, Integer> nb : neighbors) {
            if (!Pair.isValid(nb)) continue;
            Land land = map[nb.x][nb.y];
            LandType lt = land.getLandType();
            if ((lt == LandType.MEADOW || lt == LandType.PLAIN) && land.hasFoodResource()) reserved.add(nb);
        }

        for (LandType targetType : new LandType[]{ LandType.MEADOW, LandType.PLAIN }) {
            boolean satisfied = false;
            for (Pair<Integer, Integer> nb : neighbors) {
                if (!Pair.isValid(nb)) continue;
                Land land = map[nb.x][nb.y];
                if (land.getLandType() == targetType && land.hasFoodResource()) { satisfied = true; break; }
            }
            if (satisfied) continue;

            Pair<Integer, Integer> chosen = null;
            for (Pair<Integer, Integer> nb : neighbors) {
                if (!Pair.isValid(nb) || reserved.contains(nb)) continue;
                if (map[nb.x][nb.y].getLandType() != LandType.MOUNTAIN) { chosen = nb; break; }
            }
            if (chosen == null) {
                for (Pair<Integer, Integer> nb : neighbors) {
                    if (Pair.isValid(nb) && !reserved.contains(nb)) { chosen = nb; break; }
                }
            }
            if (chosen == null) continue;

            reserved.add(chosen);
            Land land = map[chosen.x][chosen.y];
            land.setLandType(targetType);
            land.setResource(new Resource(ResourceType.FOOD));
        }
    }

    // makes sure at least one town hall neighbor is forest, so lumber mill is buildable at
    // start too. needs to run after ensureStartingFoodTiles so it doesn't steal back a tile
    // that call already claimed for food
    private static void ensureStartingForestTile(Land[][] map, Pair<Integer, Integer> thCoord) {
        ArrayList<Pair<Integer, Integer>> neighbors = getAllNeighborsIndexes(thCoord);

        for (Pair<Integer, Integer> nb : neighbors) {
            if (!Pair.isValid(nb)) continue;
            if (map[nb.x][nb.y].getLandType() == LandType.FOREST) return; // already have one
        }

        // prefer a neighbor without a food resource (those belong to ensureStartingFoodTiles)
        // and not a mountain (guaranteed stone)
        Pair<Integer, Integer> chosen = null;
        for (Pair<Integer, Integer> nb : neighbors) {
            if (!Pair.isValid(nb)) continue;
            Land land = map[nb.x][nb.y];
            boolean isFoodTile = (land.getLandType() == LandType.MEADOW || land.getLandType() == LandType.PLAIN)
                    && land.hasFoodResource();
            if (!isFoodTile && land.getLandType() != LandType.MOUNTAIN) { chosen = nb; break; }
        }
        if (chosen == null) {
            for (Pair<Integer, Integer> nb : neighbors) {
                if (Pair.isValid(nb) && map[nb.x][nb.y].getLandType() != LandType.MOUNTAIN) { chosen = nb; break; }
            }
        }
        if (chosen == null) return;

        Land land = map[chosen.x][chosen.y];
        land.setLandType(LandType.FOREST);
        land.setResource(new Resource(ResourceType.WOOD)); // forest tiles always have wood
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

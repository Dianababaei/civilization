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

    // ── Phase 3: named multiplayer maps ───────────────────────────────────────
    // "pre-designed" maps are fixed seeds into the same generator: the same name
    // always produces the exact same terrain, which is what map selection in the
    // lobby needs. Spawn slots sit symmetrically so no player starts closer to an
    // opponent than the others (P3-03).

    public static long seedForMap(String mapName) {
        switch (mapName) {
            case "Green Valley": return 20261L;
            case "Twin Rivers":  return 20262L;
            case "Iron Ridge":   return 20263L;
            default:             return mapName.hashCode();
        }
    }

    public static ArrayList<Pair<Integer, Integer>> multiplayerSpawns(int playerCount) {
        ArrayList<Pair<Integer, Integer>> slots = new ArrayList<>();
        slots.add(new Pair<>(3, 3));
        slots.add(new Pair<>(11, 11));
        slots.add(new Pair<>(11, 3));
        slots.add(new Pair<>(3, 11));
        return new ArrayList<>(slots.subList(0, Math.min(playerCount, slots.size())));
    }

    public static Land[][] mapInitializerMultiplayer(String mapName, ArrayList<User> players) {
        // spec flow: players start with civil units only and TRAIN their army
        // through the Town Hall (TrainUnitRequest); no free military at spawn
        ArrayList<Pair<Integer, Integer>> spawns = multiplayerSpawns(players.size());
        Land[][] map = mapInitializer(new Random(seedForMap(mapName)), players, spawns);
        // P3-04: every start must be FAIR - open ground to build on plus stone and
        // iron within reach, no matter what the seed rolled for that corner
        for (Pair<Integer, Integer> spawn : spawns) ensureFairStartingArea(map, spawn);
        return map;
    }

    /**
     * Phase 3 map fairness (P3-04): a corner spawn must never be crippled by seed
     * luck. Deterministic (no RNG), so named maps stay identical on every load:
     *  - at least 6 open tiles (plain/meadow/forest) within radius 2, by
     *    flattening excess mountains closest-first;
     *  - at least one stone mountain within radius 3;
     *  - at least one iron deposit within radius 3.
     */
    static void ensureFairStartingArea(Land[][] map, Pair<Integer, Integer> spawn) {
        ArrayList<Pair<Integer, Integer>> reserved = new ArrayList<>();
        reserved.add(spawn);
        reserved.addAll(getAllNeighborsIndexes(spawn)); // kept for food/forest/starting units

        // 1) open working area: flatten mountains near the spawn until there is room
        int open = 0;
        ArrayList<Pair<Integer, Integer>> ring2 = DisasterController.hexesInRadius(spawn, 2);
        for (Pair<Integer, Integer> hex : ring2) {
            if (!Pair.isValid(hex) || hex.equals(spawn)) continue;
            LandType lt = map[hex.x][hex.y].getLandType();
            if (lt == LandType.PLAIN || lt == LandType.MEADOW || lt == LandType.FOREST) open++;
        }
        for (Pair<Integer, Integer> hex : ring2) {
            if (open >= 6) break;
            if (!Pair.isValid(hex) || hex.equals(spawn)) continue;
            Land tile = map[hex.x][hex.y];
            if (tile.getLandType() == LandType.MOUNTAIN || tile.getLandType() == LandType.MOUNTAIN_RANGE) {
                tile.setLandType(LandType.PLAIN);
                tile.setResource(null);
                tile.setHasIron(false);
                tile.setIronResource(null);
                open++;
            }
        }

        // 2) guaranteed stone and iron within radius 3
        ArrayList<Pair<Integer, Integer>> ring3 = DisasterController.hexesInRadius(spawn, 3);
        Land firstMountain = null;
        boolean ironNearby = false;
        for (Pair<Integer, Integer> hex : ring3) {
            if (!Pair.isValid(hex)) continue;
            Land tile = map[hex.x][hex.y];
            if (tile.getLandType() != LandType.MOUNTAIN) continue;
            if (firstMountain == null) firstMountain = tile;
            if (tile.hasIron()) ironNearby = true;
        }
        if (firstMountain == null) {
            // raise one mountain on the first free plain/meadow outside the reserved ring
            for (Pair<Integer, Integer> hex : ring3) {
                if (!Pair.isValid(hex) || reserved.contains(hex)) continue;
                Land tile = map[hex.x][hex.y];
                if (tile.getLandType() != LandType.PLAIN && tile.getLandType() != LandType.MEADOW) continue;
                if (tile.getCivilizedUnit() != null) continue;
                tile.setLandType(LandType.MOUNTAIN);
                tile.setResource(new Resource(ResourceType.STONE));
                firstMountain = tile;
                break;
            }
        }
        if (!ironNearby && firstMountain != null) {
            firstMountain.setHasIron(true);
            firstMountain.setIronResource(new Resource(ResourceType.IRON));
        }
    }

    /** Single-player path, exactly as before: random seed, one spawn at the map middle. */
    public static Land[][] mapInitializer() {
        ArrayList<Pair<Integer, Integer>> spawns = new ArrayList<>();
        spawns.add(new Pair<>(7, 7));
        return mapInitializer(new Random(Double.doubleToLongBits(Math.random())),
                Game.instance.getPlayersInGame(), spawns);
    }

    static Land[][] mapInitializer(Random random, ArrayList<User> players,
                                   ArrayList<Pair<Integer, Integer>> spawns) {
        Land[][] map = new Land[Consts.MAP_SIZE.amount.x][Consts.MAP_SIZE.amount.y];
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

        // phase 2: about a third of the mountains become Mountain Ranges - fully
        // impassable, no stone/iron, nothing buildable. never on or right next to a
        // spawn spot so every starting area always stays open. this runs BEFORE
        // resource assignment so ranges simply never get a stone deposit
        ArrayList<Pair<Integer, Integer>> protectedSpots = new ArrayList<>();
        for (Pair<Integer, Integer> spawn : spawns) {
            protectedSpots.add(spawn);
            protectedSpots.addAll(getAllNeighborsIndexes(spawn));
        }
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                if (map[i][j].getLandType() != LandType.MOUNTAIN) continue;
                if (protectedSpots.contains(new Pair<>(i, j))) continue;
                if (random.nextInt(3) == 0) map[i][j].setLandType(LandType.MOUNTAIN_RANGE);
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

        // set up every player's starting area the same way (single-player = one spawn
        // at the middle, multiplayer = one symmetric spawn slot per player).
        // start is 1 explorer + 2 builders + 2 workers around the town hall
        for (int p = 0; p < players.size() && p < spawns.size(); p++) {
            Nation nation = players.get(p).getNation();
            Pair<Integer, Integer> thCoord = spawns.get(p);

            Land townHallLand = map[thCoord.x][thCoord.y];
            TownHall townHall = new TownHall(nation, townHallLand);
            nation.setTownHall(townHall);
            // town hall tile + its neighbors count as player territory
            nation.addToTerritory(thCoord);
            for (Pair<Integer, Integer> neighbor : getAllNeighborsIndexes(thCoord)) {
                if (Pair.isValid(neighbor)) nation.addToTerritory(neighbor);
            }

            // random terrain can roll a starting area with zero food tiles, which would make
            // farm/stable impossible to build near the capital. force at least one neighbor
            // into each type (without touching the town hall tile itself). running these per
            // spawn is also the "fair access to all 4 resources" rule of phase 3 (P3-04)
            ensureStartingFoodTiles(map, thCoord);
            // same deal for lumber mill - make sure at least one neighbor is forest
            ensureStartingForestTile(map, thCoord);
            // phase 2: mountain ranges must never wall the town hall in - demote ranges
            // until every normal tile on the map can be reached from the town hall
            ensureTownHallNotEnclosed(map, thCoord);
            // town hall + neighbors are visible right from the start.
            // also counts as "explored" for border expander (it's the player's known starting
            // area, not something a unit happened to walk past)
            map[thCoord.x][thCoord.y].setVisibility(2);
            map[thCoord.x][thCoord.y].addSeerNation(nation);
            map[thCoord.x][thCoord.y].addExplorerSeenNation(nation);
            for (Pair<Integer, Integer> nb : getAllNeighborsIndexes(thCoord)) {
                if (Pair.isValid(nb)) {
                    map[nb.x][nb.y].setVisibility(2);
                    map[nb.x][nb.y].addSeerNation(nation);
                    map[nb.x][nb.y].addExplorerSeenNation(nation);
                }
            }

            // put starting units on tiles next to town hall (not on the town hall tile itself)
            int[][] startOffsets = { {-1,-1}, {-1,0}, {0,-1}, {0,1}, {1,0} };
            CivilizedUnitType[] startTypes = {
                CivilizedUnitType.EXPLORER,
                CivilizedUnitType.BUILDER,
                CivilizedUnitType.BUILDER,
                CivilizedUnitType.WORKER,
                CivilizedUnitType.WORKER
            };
            for (int s = 0; s < startTypes.length; s++) {
                Pair<Integer, Integer> loc = new Pair<>(thCoord.x + startOffsets[s][0], thCoord.y + startOffsets[s][1]);
                // skip if out of bounds or something's already there
                if (!Pair.isValid(loc) || map[loc.x][loc.y].getCivilizedUnit() != null) continue;
                CivilizedUnit unit = new CivilizedUnit(startTypes[s], nation, loc);
                map[loc.x][loc.y].setCivilizedUnit(unit);
                nation.getUnits().add(unit);
            }
        }
        Pair<Integer, Integer> thCoord = spawns.get(0);

        // ruins use the same seeded random as everything else so the named
        // multiplayer maps come out identical on every machine (phase 3)
        for (int i1 = 0; i1 < Consts.MAP_SIZE.amount.x; i1++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land land = map[i1][j];
                int chance = random.nextInt(10);
                if (chance <= 1 && land.getLandType() != LandType.MOUNTAIN
                        && land.getLandType() != LandType.MOUNTAIN_RANGE && land.getCivilizedUnit() == null)
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
            if (tile.getLandType() == LandType.MOUNTAIN || tile.getLandType() == LandType.MOUNTAIN_RANGE) continue;
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

    // phase 2: the spec says mountain ranges must never enclose the town hall. we go
    // further and demand every non-range tile is reachable from the town hall, so no
    // ruin/trading post can end up sealed off either. if the BFS from the town hall
    // can't reach some tile, we knock one range (that touches the reachable area) back
    // down to a normal mountain and try again - each pass removes one range so it
    // always terminates
    private static void ensureTownHallNotEnclosed(Land[][] map, Pair<Integer, Integer> thCoord) {
        while (true) {
            boolean[][] reachable = new boolean[Consts.MAP_SIZE.amount.x][Consts.MAP_SIZE.amount.y];
            ArrayList<Pair<Integer, Integer>> queue = new ArrayList<>();
            queue.add(thCoord);
            reachable[thCoord.x][thCoord.y] = true;
            while (!queue.isEmpty()) {
                Pair<Integer, Integer> cur = queue.remove(queue.size() - 1);
                for (Pair<Integer, Integer> nb : getAllNeighborsIndexes(cur)) {
                    if (!Pair.isValid(nb) || reachable[nb.x][nb.y]) continue;
                    if (map[nb.x][nb.y].getLandType() == LandType.MOUNTAIN_RANGE) continue;
                    reachable[nb.x][nb.y] = true;
                    queue.add(nb);
                }
            }

            boolean allReachable = true;
            for (int i = 0; i < Consts.MAP_SIZE.amount.x && allReachable; i++)
                for (int j = 0; j < Consts.MAP_SIZE.amount.y && allReachable; j++)
                    if (!reachable[i][j] && map[i][j].getLandType() != LandType.MOUNTAIN_RANGE)
                        allReachable = false;
            if (allReachable) return;

            // open a passage: prefer a range tile that sits right between the reachable
            // area and a sealed-off normal tile, otherwise any range on the frontier
            Pair<Integer, Integer> demote = null;
            Pair<Integer, Integer> fallback = null;
            for (int i = 0; i < Consts.MAP_SIZE.amount.x && demote == null; i++) {
                for (int j = 0; j < Consts.MAP_SIZE.amount.y && demote == null; j++) {
                    if (map[i][j].getLandType() != LandType.MOUNTAIN_RANGE) continue;
                    boolean touchesReachable = false, touchesSealed = false;
                    for (Pair<Integer, Integer> nb : getAllNeighborsIndexes(new Pair<>(i, j))) {
                        if (!Pair.isValid(nb)) continue;
                        if (reachable[nb.x][nb.y]) touchesReachable = true;
                        else if (map[nb.x][nb.y].getLandType() != LandType.MOUNTAIN_RANGE) touchesSealed = true;
                    }
                    if (touchesReachable && touchesSealed) demote = new Pair<>(i, j);
                    else if (touchesReachable && fallback == null) fallback = new Pair<>(i, j);
                }
            }
            if (demote == null) demote = fallback;
            if (demote == null) return; // no range touches the reachable area - nothing more we can do

            Land land = map[demote.x][demote.y];
            land.setLandType(LandType.MOUNTAIN);
            // demoted back to a normal mountain, so it gets the guaranteed stone deposit
            if (land.getResource() == null) land.setResource(new Resource(ResourceType.STONE));
        }
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
                        } else if (areNeighbors(new Pair<>(i1, j1), new Pair<>(i2, j2))
                                // phase 2: mountain ranges are 100% impassable - leave
                                // them at the "unreachable" distance like non-neighbors
                                && land1.getLandType().isWalkable && land2.getLandType().isWalkable) {
                            // getMPFrom applies the road discount when both hexes have one
                            Game.instance.dist[num1][num2] = land2.getMPFrom(land1);
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

package sut.civilization.Controller.GameControllers;

import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.*;
import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.EnemyType;

import java.util.ArrayList;

/**
 * Phase 2 dice combat (P2-14..17).
 *
 * Core rules from the spec:
 *  - one die per attacking unit (we keep one unit per hex, so attacks roll 1 die)
 *  - defender rolls 2 dice (barbarian / our own units) or 1 die (wild animal)
 *  - both sides sort descending and compare pair by pair; TIES FAVOR THE DEFENDER
 *  - each lost pair = 1 hit (swordsman/archer die at 1 hit, cavalry survives 2)
 *  - only archers can attack at distance 2, everyone fights adjacent
 *  - every participant spends 1 AP
 *  - structures (camp, town hall) take NO dice: flat damage = attack power, and
 *    only after the defending units on the hex are dead (P2-16)
 *  - a battle must never resolve silently: everything is packed into a
 *    BattleReport so the UI can show the dice graphically AND numerically (P2-15)
 */
public class CombatController extends GameController {

    /** Everything the UI needs to show one battle: dice, per-pair outcomes, summary. */
    public static class BattleReport {
        public String title = "";
        /** Sorted descending. Empty for structure damage (no dice there). */
        public int[] attackerDice = new int[0];
        public int[] defenderDice = new int[0];
        /** One line per compared pair (plus explanation lines for no-dice damage). */
        public final ArrayList<String> lines = new ArrayList<>();
        public String summary = "";
    }

    /**
     * Enemy attacks that happened during turn processing. The UI pops a dice dialog
     * for each one after End Turn (same pattern as DisasterController.lastQuakeArea);
     * headless drivers just read the list. Refilled at the start of every enemy turn.
     */
    public static final ArrayList<BattleReport> lastEnemyBattles = new ArrayList<>();

    // ── Dice ──────────────────────────────────────────────────────────────────

    /** One die from the game's own saved RNG, so dice sequences survive save/load (P2-66). */
    static int rollDie() {
        return Game.instance.getRng().nextInt(6) + 1;
    }

    static int[] rollDice(int count) {
        int[] dice = new int[count];
        for (int i = 0; i < count; i++) dice[i] = rollDie();
        // sort descending (tiny arrays, a bubble pass is fine)
        for (int a = 0; a < count; a++)
            for (int b = a + 1; b < count; b++)
                if (dice[b] > dice[a]) { int t = dice[a]; dice[a] = dice[b]; dice[b] = t; }
        return dice;
    }

    /**
     * Compares already-sorted dice pair by pair. Ties favor the defender (P2-14).
     * Returns {hits the defender takes, hits the attacker takes}.
     * Public so the headless driver can verify the rules with fixed dice.
     */
    public static int[] comparePairs(int[] attackerDice, int[] defenderDice) {
        int pairs = Math.min(attackerDice.length, defenderDice.length);
        int toDefender = 0, toAttacker = 0;
        for (int p = 0; p < pairs; p++) {
            if (attackerDice[p] > defenderDice[p]) toDefender++;
            else toAttacker++;
        }
        return new int[]{toDefender, toAttacker};
    }

    // ── Player attacks ────────────────────────────────────────────────────────

    /** Hexes with an enemy that this unit could attack right now. */
    public static ArrayList<Pair<Integer, Integer>> getTargetsInRange(CivilizedUnit unit) {
        ArrayList<Pair<Integer, Integer>> targets = new ArrayList<>();
        if (unit == null) return targets;
        for (Pair<Integer, Integer> hex : DisasterController.hexesInRadius(unit.getLocation(), unit.getCivilizedUnitType().attackRange)) {
            if (hex.equals(unit.getLocation())) continue;
            Land tile = Game.instance.map[hex.x][hex.y];
            // can't attack into fog - you have to see what you're shooting at
            if (tile.getEnemy() != null && tile.getVisibility() == 2) targets.add(hex);
        }
        return targets;
    }

    /** Why this unit can't attack at all right now, or null if it can. */
    public static String checkCanAttack(CivilizedUnit unit) {
        if (unit == null) return "Select a unit first!";
        if (!unit.getCivilizedUnitType().isMilitary) return "Only military units can attack!";
        if (!unit.hasAp()) return "No AP left!";
        if (getTargetsInRange(unit).isEmpty()) return "No enemy in range!";
        return null;
    }

    /** Why this unit can't attack this specific hex, or null if it can. */
    public static String checkCanAttackTarget(CivilizedUnit unit, Pair<Integer, Integer> target) {
        String general = checkCanAttack(unit);
        if (general != null) return general;
        Land tile = Game.instance.map[target.x][target.y];
        if (tile.getEnemy() == null) return "Nothing to attack there!";
        int distance = hexDistance(unit.getLocation(), target);
        if (distance > unit.getCivilizedUnitType().attackRange) return "Target is out of range!";
        // spec: at distance 2 only archers may attack (and even they roll just their 1 die)
        if (distance == 2 && unit.getCivilizedUnitType() != CivilizedUnitType.ARCHER)
            return "Only Archers can attack at distance 2!";
        return null;
    }

    public static int hexDistance(Pair<Integer, Integer> a, Pair<Integer, Integer> b) {
        if (a.equals(b)) return 0;
        int ring = 1;
        ArrayList<Pair<Integer, Integer>> area = DisasterController.hexesInRadius(a, 1);
        while (!area.contains(b) && ring < 5) {
            ring++;
            area = DisasterController.hexesInRadius(a, ring);
        }
        return ring;
    }

    /**
     * Resolves one attack. Call {@link #checkCanAttackTarget} first - this assumes
     * the attack is legal. Never returns null and never resolves silently.
     */
    public static BattleReport attack(CivilizedUnit attacker, Pair<Integer, Integer> target) {
        Land tile = Game.instance.map[target.x][target.y];
        Enemy enemy = tile.getEnemy();
        CivilizedUnitType type = attacker.getCivilizedUnitType();
        attacker.spendAp(1); // spec: every participant spends 1 AP

        BattleReport report = new BattleReport();

        // structure damage (P2-16): no dice, flat attack power - but only once the
        // camp's guard is dead ("units must be cleared before the structure")
        if (enemy.getType() == EnemyType.CAMP && !enemy.hasLivingGuard()) {
            int damage = type.attackPower;
            enemy.setHp(enemy.getHp() - damage);
            report.title = type.name + " attacks the " + enemy.getType().name;
            report.lines.add("Structures roll no dice - damage = the attacker's attack power.");
            report.lines.add(type.name + " deals " + damage + " damage to the camp.");
            if (enemy.getHp() <= 0) {
                tile.setEnemy(null);
                report.summary = "The " + enemy.getType().name + " is DESTROYED!";
            } else {
                report.summary = "Camp HP: " + enemy.getHp() + "/" + enemy.getType().maxHp;
            }
            return report;
        }

        // dice battle against the enemy unit (or the camp's guard)
        boolean guardFight = enemy.getType() == EnemyType.CAMP;
        String defenderName = guardFight ? "Camp guard (Barbarian)" : enemy.getType().name;
        int defenderDiceCount = guardFight ? EnemyType.BARBARIAN.defenseDice : enemy.getType().defenseDice;

        report.title = type.name + " attacks " + defenderName;
        report.attackerDice = rollDice(1);
        report.defenderDice = rollDice(defenderDiceCount);
        int[] hits = comparePairs(report.attackerDice, report.defenderDice);
        describePairs(report, type.name, defenderName);

        if (guardFight) {
            enemy.setGuardHp(enemy.getGuardHp() - hits[0]);
            if (!enemy.hasLivingGuard())
                report.summary = "The camp guard is dead - the camp structure is exposed now!";
            else
                report.summary = "Guard HP left: " + enemy.getGuardHp() + "/" + Enemy.CAMP_GUARD_HP;
        } else {
            // P2-57: the bear is an HP pool (120) - a won pair hits it for the
            // attacker's attack power. Barbarians stay on the 1-hit scale.
            int damagePerWin = enemy.getType() == EnemyType.BEAR ? type.attackPower : 1;
            if (hits[0] > 0 && damagePerWin > 1)
                report.lines.add("Each won pair hits the bear for the attacker's attack power (" + damagePerWin + ").");
            enemy.setHp(enemy.getHp() - hits[0] * damagePerWin);
            if (enemy.getHp() <= 0) {
                tile.setEnemy(null);
                report.summary = defenderName + " is killed!";
            } else if (hits[0] > 0) {
                report.summary = defenderName + " HP left: " + enemy.getHp() + "/" + enemy.getType().maxHp;
            }
        }

        if (hits[1] > 0) {
            attacker.setHp(attacker.getHp() - hits[1]);
            if (attacker.getHp() <= 0) {
                DisasterController.removeDeadUnit(attacker);
                report.summary = (report.summary.isEmpty() ? "" : report.summary + " ")
                        + "Your " + type.name + " died in the battle!";
            } else {
                report.summary = (report.summary.isEmpty() ? "" : report.summary + " ")
                        + "Your " + type.name + " HP: " + attacker.getHp() + "/" + type.maxHp;
            }
        }
        return report;
    }

    private static void describePairs(BattleReport report, String attackerName, String defenderName) {
        int pairs = Math.min(report.attackerDice.length, report.defenderDice.length);
        for (int p = 0; p < pairs; p++) {
            int a = report.attackerDice[p], d = report.defenderDice[p];
            String outcome;
            if (a > d) outcome = "attacker wins - " + defenderName + " takes 1 hit";
            else if (a == d) outcome = "TIE, defender wins - " + attackerName + " takes 1 hit";
            else outcome = "defender wins - " + attackerName + " takes 1 hit";
            report.lines.add("Pair " + (p + 1) + ": " + a + " vs " + d + "  ->  " + outcome);
        }
        int unmatched = Math.abs(report.attackerDice.length - report.defenderDice.length);
        if (unmatched > 0)
            report.lines.add("(" + unmatched + " leftover die/dice with no pair - ignored)");
    }

    // ── Enemy turn (P2-17: defensive battles) ─────────────────────────────────

    /**
     * Every barbarian and bear on the map attacks once, at the start of turn
     * processing. Barbarians fight our units with dice (we defend with 2 dice per
     * spec); bears hit directly - 1 hit to a military unit, {@code attackPower}
     * raw HP damage to civilians and the Town Hall (the audit's "animals damage
     * HP directly" rule). Damage priority: military targets before civilians.
     */
    public static void enemyTurn() {
        lastEnemyBattles.clear();

        // collect first, then act - an attack must not disturb the map scan
        ArrayList<Enemy> enemies = new ArrayList<>();
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                if (Game.instance.map[i][j].getEnemy() != null)
                    enemies.add(Game.instance.map[i][j].getEnemy());

        for (Enemy enemy : enemies) {
            if (enemy.getType() == EnemyType.CAMP) continue; // structures don't attack
            if (enemy.getType() == EnemyType.BEAR) continue; // bears roam via DisasterController.bearTurn()

            // barbarian picks a target next door: military first, then civilians, then the TH
            Land militaryTile = null, civilianTile = null, townHallTile = null;
            for (Pair<Integer, Integer> nb : LandController.getAllNeighborsIndexes(enemy.getLocation())) {
                if (!Pair.isValid(nb)) continue;
                Land tile = Game.instance.map[nb.x][nb.y];
                CivilizedUnit unit = tile.getCivilizedUnit();
                if (unit != null && unit.getCivilizedUnitType().isMilitary && militaryTile == null) militaryTile = tile;
                else if (unit != null && !unit.getCivilizedUnitType().isMilitary && civilianTile == null) civilianTile = tile;
                for (User u : Game.instance.getPlayersInGame()) {
                    TownHall th = u.getNation().getTownHall();
                    if (th != null && th.getMainLand() == tile) townHallTile = tile;
                }
            }

            if (militaryTile != null || civilianTile != null) {
                Land targetTile = militaryTile != null ? militaryTile : civilianTile;
                lastEnemyBattles.add(barbarianAttacksUnit(enemy, targetTile.getCivilizedUnit()));
            } else if (townHallTile != null) {
                lastEnemyBattles.add(enemyAttacksTownHall(enemy, townHallTile));
            }
            // nothing of ours next door: the enemy just lurks
        }
    }

    /** Barbarian attack = a normal dice battle where WE are the defender with 2 dice. */
    private static BattleReport barbarianAttacksUnit(Enemy barbarian, CivilizedUnit defender) {
        BattleReport report = new BattleReport();
        String defenderName = "your " + defender.getCivilizedUnitType().name;
        report.title = "Barbarian attacks " + defenderName + " at ("
                + defender.getLocation().x + "," + defender.getLocation().y + ")";
        report.attackerDice = rollDice(1);          // one die per attacking unit
        report.defenderDice = rollDice(2);          // spec: we defend with 2 dice
        int[] hits = comparePairs(report.attackerDice, report.defenderDice);
        describePairs(report, "Barbarian", defenderName);

        if (hits[0] > 0) {
            defender.setHp(defender.getHp() - hits[0]);
            if (defender.getHp() <= 0) {
                DisasterController.removeDeadUnit(defender);
                report.summary = "Your " + defender.getCivilizedUnitType().name + " was killed!";
            } else {
                report.summary = "Your " + defender.getCivilizedUnitType().name + " HP: "
                        + defender.getHp() + "/" + defender.getCivilizedUnitType().maxHp;
            }
        }
        if (hits[1] > 0) {
            barbarian.setHp(barbarian.getHp() - hits[1]);
            if (barbarian.getHp() <= 0) {
                removeEnemy(barbarian);
                report.summary = (report.summary.isEmpty() ? "" : report.summary + " ")
                        + "The Barbarian died attacking!";
            } else {
                report.summary = (report.summary.isEmpty() ? "" : report.summary + " ")
                        + "Barbarian HP: " + barbarian.getHp() + "/" + EnemyType.BARBARIAN.maxHp;
            }
        }
        notifyTurn(report.title + " - " + report.summary);
        return report;
    }

    /** Bear attack = no dice (audit rule): 1 hit vs military, raw 35 HP vs a civilian.
     *  Called from DisasterController.bearTurn(), which handles movement and targeting. */
    static BattleReport bearAttacksUnit(Enemy bear, CivilizedUnit defender) {
        BattleReport report = new BattleReport();
        CivilizedUnitType type = defender.getCivilizedUnitType();
        report.title = "Bear attacks your " + type.name + " at ("
                + defender.getLocation().x + "," + defender.getLocation().y + ")";
        if (type.isMilitary) {
            report.lines.add("Wild animals strike directly - no dice. A military unit takes 1 hit.");
            defender.setHp(defender.getHp() - 1);
        } else {
            report.lines.add("Wild animals strike directly - no dice. " + type.name
                    + " takes " + EnemyType.BEAR.attackPower + " HP damage.");
            defender.setHp(defender.getHp() - EnemyType.BEAR.attackPower);
        }
        if (defender.getHp() <= 0) {
            DisasterController.removeDeadUnit(defender);
            report.summary = "Your " + type.name + " was killed by the bear!";
        } else {
            report.summary = "Your " + type.name + " HP: " + defender.getHp() + "/" + type.maxHp;
        }
        notifyTurn(report.title + " - " + report.summary);
        return report;
    }

    /** Structure damage against our Town Hall: flat attack power, but never below 1 HP. */
    private static BattleReport enemyAttacksTownHall(Enemy enemy, Land townHallTile) {
        BattleReport report = new BattleReport();
        report.title = enemy.getType().name + " attacks your Town Hall!";
        for (User u : Game.instance.getPlayersInGame()) {
            TownHall th = u.getNation().getTownHall();
            if (th != null && th.getMainLand() == townHallTile) {
                // P2-07: the Town Hall's defense soaks damage (10 base, 30 with the
                // Defensive Architecture wall) - but every attack still chips at least 1
                int damage = Math.max(1, enemy.getType().attackPower - th.getDefense());
                th.setHp(Math.max(1, th.getHp() - damage));
                report.lines.add("Structures roll no dice - the "
                        + enemy.getType().name + " attacks with " + enemy.getType().attackPower
                        + ", defense " + th.getDefense()
                        + (th.hasStoneWall() ? " (stone wall)" : "")
                        + " soaks it down to " + damage + " damage.");
                report.summary = "Town Hall HP: " + th.getHp() + "/" + th.getMaxHp();
            }
        }
        notifyTurn(report.title + " - " + report.summary);
        return report;
    }

    static void removeEnemy(Enemy enemy) {
        Pair<Integer, Integer> loc = enemy.getLocation();
        if (Pair.isValid(loc) && Game.instance.map[loc.x][loc.y].getEnemy() == enemy)
            Game.instance.map[loc.x][loc.y].setEnemy(null);
    }
}

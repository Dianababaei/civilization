package sut.civilization.Network.Server;

import sut.civilization.Controller.GameControllers.CombatController;
import sut.civilization.Controller.GameControllers.GameController;
import sut.civilization.Controller.GameControllers.LandController;
import sut.civilization.Controller.GameControllers.UnitController;
import sut.civilization.Enums.Consts;
import sut.civilization.Model.Classes.CivilizedUnit;
import sut.civilization.Model.Classes.Game;
import sut.civilization.Model.Classes.Land;
import sut.civilization.Model.Classes.Nation;
import sut.civilization.Model.Classes.Pair;
import sut.civilization.Model.Classes.TownHall;
import sut.civilization.Model.Classes.TradeOffer;
import sut.civilization.Model.Classes.Unit;
import sut.civilization.Model.Classes.User;
import sut.civilization.Model.ModulEnums.CivilizedUnitType;
import sut.civilization.Model.ModulEnums.LandType;
import sut.civilization.Model.ModulEnums.Relation;
import sut.civilization.Network.Messages.ActionResultResponse;
import sut.civilization.Network.Messages.AllianceReplyRequest;
import sut.civilization.Network.Messages.AttackRequest;
import sut.civilization.Network.Messages.BattleReportMessage;
import sut.civilization.Network.Messages.BreakAllianceRequest;
import sut.civilization.Network.Messages.DebugActionRequest;
import sut.civilization.Network.Messages.DeclareWarRequest;
import sut.civilization.Network.Messages.EndTurnRequest;
import sut.civilization.Network.Messages.ErrorResponse;
import sut.civilization.Network.Messages.GameStateBroadcast;
import sut.civilization.Network.Messages.Message;
import sut.civilization.Network.Messages.MoveUnitRequest;
import sut.civilization.Network.Messages.OfferAllianceRequest;
import sut.civilization.Network.Messages.TradeOfferRequest;
import sut.civilization.Network.Messages.TradeReplyRequest;
import sut.civilization.Network.Messages.TrainUnitRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The running match on the server. Owns THE game (this JVM's Game.instance)
 * and is the only writer to it: every request comes in through a synchronized
 * method, so two client threads can never mutate the game at the same time.
 *
 * Validation reuses the phase 1/2 controllers unchanged - the server points
 * GameController.currentTurnUser at the requesting player and calls the same
 * code the single-player game runs, so every AP/terrain/ownership rule applies
 * automatically (see docs/phase3-architecture.md, ADR 4).
 */
public class GameSession {
    private final List<ClientHandler> players;
    private int currentIndex = 0;
    /**
     * War reports waiting for their reader (P3-34): a player attacked during an
     * opponent's turn gets the full dice report the moment their own turn starts.
     * The attacker's copy is flushed right away (they are the current player).
     */
    private final Map<String, List<BattleReportMessage>> pendingReports = new HashMap<>();

    public GameSession(List<ClientHandler> players, String mapName) {
        this.players = new ArrayList<>(players);

        // build the authoritative game from the lobby roster
        ArrayList<User> users = new ArrayList<>();
        for (ClientHandler h : this.players) {
            User user = new User(h.getUsername(), "", h.getUsername());
            user.setNation(new Nation());
            users.add(user);
        }
        Game.instance = new Game();
        Game.instance.setPlayersInGame(users);
        GameController.setCurrentTurnUser(users.get(0));
        Game.instance.setMap(LandController.mapInitializerMultiplayer(mapName, users));
        LandController.initializeDistances();
        LandController.updateDistances();

        broadcastState(new ArrayList<>());
    }

    /**
     * Everything a client may do during the match funnels through here.
     * The verdict goes back first, then (on success) everyone gets fresh
     * snapshots - so the requester always sees WHY before the screen changes.
     */
    public synchronized void handle(ClientHandler handler, Message message) {
        List<String> notifications = new ArrayList<>();
        ActionResultResponse result;
        try {
            if (Game.instance.getWinner() != null) {
                result = new ActionResultResponse(false,
                        "The game is over - " + Game.instance.getWinner() + " won.");
            } else if (Game.instance.isEliminated(handler.getUsername())) {
                result = new ActionResultResponse(false, "You have been eliminated from this game.");
            } else if (message instanceof MoveUnitRequest) {
                result = handleMove(handler, (MoveUnitRequest) message);
            } else if (message instanceof EndTurnRequest) {
                result = handleEndTurn(handler, notifications);
            } else if (message instanceof AttackRequest) {
                result = handleAttack(handler, (AttackRequest) message, notifications);
            } else if (message instanceof DeclareWarRequest) {
                result = handleDeclareWar(handler, (DeclareWarRequest) message, notifications);
            } else if (message instanceof OfferAllianceRequest) {
                result = handleOfferAlliance(handler, (OfferAllianceRequest) message);
            } else if (message instanceof AllianceReplyRequest) {
                result = handleAllianceReply(handler, (AllianceReplyRequest) message, notifications);
            } else if (message instanceof BreakAllianceRequest) {
                result = handleBreakAlliance(handler, (BreakAllianceRequest) message, notifications);
            } else if (message instanceof TrainUnitRequest) {
                result = handleTrainUnit(handler, (TrainUnitRequest) message);
            } else if (message instanceof DebugActionRequest) {
                result = handleDebugAction(handler, (DebugActionRequest) message, notifications);
            } else if (message instanceof sut.civilization.Network.Messages.CheatCommandRequest) {
                result = handleCheatCommand(handler,
                        (sut.civilization.Network.Messages.CheatCommandRequest) message, notifications);
            } else if (message instanceof sut.civilization.Network.Messages.UpgradeTownHallRequest) {
                result = handleUpgradeTownHall(handler);
            } else if (message instanceof sut.civilization.Network.Messages.ResearchRequest) {
                result = handleResearch(handler, (sut.civilization.Network.Messages.ResearchRequest) message);
            } else if (message instanceof sut.civilization.Network.Messages.BuildImprovementRequest) {
                result = handleBuildImprovement(handler, (sut.civilization.Network.Messages.BuildImprovementRequest) message);
            } else if (message instanceof sut.civilization.Network.Messages.StationRequest) {
                result = handleStation(handler, (sut.civilization.Network.Messages.StationRequest) message);
            } else if (message instanceof TradeOfferRequest) {
                result = handleTradeOffer(handler, (TradeOfferRequest) message);
            } else if (message instanceof TradeReplyRequest) {
                result = handleTradeReply(handler, (TradeReplyRequest) message);
            } else {
                result = new ActionResultResponse(false,
                        "The server does not accept \"" + message.getType() + "\" during the game.");
            }
        } catch (Exception e) {
            // a request must never take the handler thread (and the client) down
            result = new ActionResultResponse(false, "Server error while handling that: " + e);
        }
        handler.send(result);
        if (result.isOk()) {
            broadcastState(notifications);
            // the current player's queued war reports go out AFTER the fresh
            // snapshot, so the dice dialog opens over an up-to-date screen
            flushReportsFor(currentPlayerName());
        }
    }

    private ActionResultResponse handleMove(ClientHandler handler, MoveUnitRequest move) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        if (!inBounds(move.getFromX(), move.getFromY()) || !inBounds(move.getToX(), move.getToY()))
            return new ActionResultResponse(false, "That hex is not on the map.");

        User user = userOf(handler);
        CivilizedUnit unit = Game.instance.map[move.getFromX()][move.getFromY()].getCivilizedUnit();
        if (unit == null)
            return new ActionResultResponse(false, "There is no unit on that hex.");
        if (unit.getOwnerNation() != user.getNation())
            return new ActionResultResponse(false, "That unit is not yours.");

        // same calls the single-player UI makes, now server-side
        GameController.setCurrentTurnUser(user);
        GameController.setSelectedCivilizedUnit(unit);
        String result = UnitController.unitSetPath(move.getToX(), move.getToY(), 0);
        boolean accepted = UnitController.hasMoreSteps(unit);
        if (accepted) {
            while (UnitController.hasMoreSteps(unit)) UnitController.unitGoForward(unit);
            // walking can reveal new tiles for this nation right away
            LandController.updateLandVisibility();
        }
        GameController.setSelectedCivilizedUnit(null);
        return new ActionResultResponse(accepted, result);
    }

    private ActionResultResponse handleEndTurn(ClientHandler handler, List<String> notifications) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);

        advanceTurn(notifications);
        return new ActionResultResponse(true, "Turn passed to " + currentPlayerName() + ".");
    }

    /**
     * Moves the turn to the next living player. Whenever the rotation passes the
     * top of the list, one full round is complete and the phase-2 world step runs
     * once (production, upkeep, seasons, disasters, barbarians, bears, autosave).
     * Eliminated players stay connected as watchers but are skipped here.
     */
    private void advanceTurn(List<String> notifications) {
        if (!anyoneAlive()) return;
        currentIndex = (currentIndex + 1) % players.size();
        if (currentIndex == 0) runWorldStep(notifications);
        skipEliminatedSeats(notifications);
    }

    private boolean anyoneAlive() {
        for (ClientHandler h : players)
            if (!Game.instance.isEliminated(h.getUsername())) return true;
        return false;
    }

    /** One full round is over: the phase-2 end-of-round world processing runs once. */
    private void runWorldStep(List<String> notifications) {
        GameController.setCurrentTurnUser(userOf(players.get(0)));
        GameController.nextGameTurn();
        notifications.addAll(GameController.turnNotifications);
        // barbarian/bear battles from the world step: the FULL dice dialog goes to
        // the victim (and only the victim), delivered when their turn starts -
        // same rule as PvP war reports (P3-34, "never a silent battle")
        for (CombatController.BattleReport report : CombatController.lastEnemyBattles) {
            String owner = GameController.nameOf(report.victim);
            if (owner != null) queueReport(owner, report);
        }
        CombatController.lastEnemyBattles.clear();
    }

    /** Walks forward while the seat the turn landed on belongs to an eliminated player. */
    private void skipEliminatedSeats(List<String> notifications) {
        int guard = 0;
        while (Game.instance.isEliminated(players.get(currentIndex).getUsername())
                && guard++ <= players.size()) {
            currentIndex = (currentIndex + 1) % players.size();
            if (currentIndex == 0) runWorldStep(notifications);
        }
    }

    /** Null when it is this handler's turn, otherwise the exact complaint. */
    private String checkTurn(ClientHandler handler) {
        if (players.get(currentIndex) != handler)
            return "It is not your turn - it is " + currentPlayerName() + "'s turn.";
        return null;
    }

    public synchronized String currentPlayerName() {
        return players.get(currentIndex).getUsername();
    }

    /** A player dropped: skip them so the game never freezes waiting on a ghost. */
    public synchronized void playerLeft(ClientHandler handler) {
        int index = players.indexOf(handler);
        if (index < 0) return;
        boolean wasTheirTurn = index == currentIndex;
        players.remove(index);
        if (players.isEmpty()) return;
        // a dropped player forfeits: their pending trades unlock (senders refunded)
        // and their alliance offers die - nothing may stay locked forever (P3-23)
        GameController.cancelPendingDealsOf(handler.getUsername());
        if (wasTheirTurn) {
            // their turn ends automatically and play moves on (P3-23). After the
            // removal their old seat holds the NEXT player - unless they sat at
            // the end of the list, which means the round just completed
            List<String> notifications = new ArrayList<>();
            boolean roundComplete = index >= players.size();
            currentIndex = roundComplete ? 0 : index;
            if (anyoneAlive()) {
                if (roundComplete) runWorldStep(notifications);
                skipEliminatedSeats(notifications);
            }
            broadcastState(notifications);
            flushReportsFor(currentPlayerName());
        } else {
            if (index < currentIndex) currentIndex--;
            broadcastState(new ArrayList<>());
        }
    }

    // ── phase 3 PvP: attacks (P3-31..34) ─────────────────────────────────────

    private ActionResultResponse handleAttack(ClientHandler handler, AttackRequest attack, List<String> notifications) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        if (!inBounds(attack.getFromX(), attack.getFromY()) || !inBounds(attack.getToX(), attack.getToY()))
            return new ActionResultResponse(false, "That hex is not on the map.");

        User user = userOf(handler);
        CivilizedUnit attacker = Game.instance.map[attack.getFromX()][attack.getFromY()].getCivilizedUnit();
        if (attacker == null)
            return new ActionResultResponse(false, "There is no unit on that hex.");
        if (attacker.getOwnerNation() != user.getNation())
            return new ActionResultResponse(false, "That unit is not yours.");

        Pair<Integer, Integer> target = new Pair<>(attack.getToX(), attack.getToY());
        String blocked = CombatController.checkCanAttackHexPvp(attacker, target);
        if (blocked != null) return new ActionResultResponse(false, blocked);

        GameController.setCurrentTurnUser(user);
        Land targetTile = Game.instance.map[target.x][target.y];
        CombatController.BattleReport report;

        if (targetTile.getEnemy() != null) {
            // barbarians / bears / camps still exist in multiplayer - same phase 2 fight
            report = CombatController.attack(attacker, target);
        } else {
            // find whose unit or Town Hall stands there
            User defender = ownerOfHex(targetTile);
            if (defender == null)
                return new ActionResultResponse(false, "Nothing to attack on that hex.");
            if (defender == user)
                return new ActionResultResponse(false, "You cannot attack your own forces.");
            Relation relation = Game.instance.relationBetween(user.getUsername(), defender.getUsername());
            if (relation != Relation.ENEMY)
                return new ActionResultResponse(false,
                        "You are not at war with " + defender.getUsername() + " - declare war first.");

            report = CombatController.pvpAttack(attacker, target, defender.getNation(), defender.getUsername());

            // war report for the victim, delivered when their turn starts (P3-34)
            queueReport(defender.getUsername(), report);

            // a fallen Town Hall can end the whole game right here (P3-01)
            notifications.addAll(GameController.checkEliminations());
        }

        // the attacker sees the dice immediately (flushed right after the broadcast)
        queueReport(handler.getUsername(), report);
        return new ActionResultResponse(true, report.title + " - " + report.summary);
    }

    /** The player whose unit or Town Hall occupies this tile, or null. */
    private User ownerOfHex(Land tile) {
        CivilizedUnit unit = tile.getCivilizedUnit();
        for (User u : Game.instance.getPlayersInGame()) {
            if (unit != null && unit.getOwnerNation() == u.getNation()) return u;
            TownHall th = u.getNation().getTownHall();
            if (th != null && th.getMainLand() == tile) return u;
        }
        return null;
    }

    private void queueReport(String username, CombatController.BattleReport report) {
        pendingReports.computeIfAbsent(username, k -> new ArrayList<>())
                .add(new BattleReportMessage(report.title, report.attackerDice,
                        report.defenderDice, report.lines, report.summary));
    }

    private void flushReportsFor(String username) {
        List<BattleReportMessage> queued = pendingReports.remove(username);
        if (queued == null) return;
        for (ClientHandler h : players)
            if (h.getUsername().equals(username))
                for (BattleReportMessage report : queued) h.send(report);
    }

    // ── phase 3: training units through the Town Hall (spec flow) ────────────

    /**
     * Queues one unit in the requester's Town Hall by reusing the full phase 2
     * validation (resources, caps, level, stable) - the queue itself already
     * advances server-side inside the end-of-round world step, and the finished
     * unit spawns next to the Town Hall exactly like single player.
     */
    private ActionResultResponse handleTrainUnit(ClientHandler handler, TrainUnitRequest request) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);

        User user = userOf(handler);
        GameController.setCurrentTurnUser(user);
        TownHall townHall = user.getNation().getTownHall();
        int queuedBefore = townHall == null ? 0 : townHall.getProductionQueue().size();
        String message = sut.civilization.Controller.GameControllers.TownHallController
                .enqueueUnit(request.getUnitName());
        boolean ok = townHall != null && townHall.getProductionQueue().size() > queuedBefore;
        return new ActionResultResponse(ok, message);
    }

    /** The unit on (x,y) if it belongs to the requester, else null (sets `problem[0]`). */
    private CivilizedUnit ownUnitAt(ClientHandler handler, int x, int y, String[] problem) {
        if (!inBounds(x, y)) {
            problem[0] = "That hex is not on the map.";
            return null;
        }
        CivilizedUnit unit = Game.instance.map[x][y].getCivilizedUnit();
        if (unit == null) {
            problem[0] = "There is no unit on that hex.";
            return null;
        }
        if (unit.getOwnerNation() != userOf(handler).getNation()) {
            problem[0] = "That unit is not yours.";
            return null;
        }
        return unit;
    }

    /** Builder constructs on its own tile - full phase 1/2 validation reused. */
    private ActionResultResponse handleBuildImprovement(ClientHandler handler,
                                                        sut.civilization.Network.Messages.BuildImprovementRequest request) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        String[] problem = new String[1];
        CivilizedUnit builder = ownUnitAt(handler, request.getX(), request.getY(), problem);
        if (builder == null) return new ActionResultResponse(false, problem[0]);

        GameController.setCurrentTurnUser(userOf(handler));
        GameController.setSelectedCivilizedUnit(builder);
        Land tile = Game.instance.map[request.getX()][request.getY()];
        sut.civilization.Model.Classes.Improvement before = tile.getImprovement();
        String message = sut.civilization.Controller.GameControllers.WorkerController
                .builderBuildImprovement(request.getImprovementName());
        GameController.setSelectedCivilizedUnit(null);
        boolean ok = tile.getImprovement() != null && tile.getImprovement() != before;
        if (ok) LandController.updateLandVisibility();
        return new ActionResultResponse(ok, message);
    }

    /** Worker stations into (or leaves) the improvement on its tile. */
    private ActionResultResponse handleStation(ClientHandler handler,
                                               sut.civilization.Network.Messages.StationRequest request) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        String[] problem = new String[1];
        CivilizedUnit worker = ownUnitAt(handler, request.getX(), request.getY(), problem);
        if (worker == null) return new ActionResultResponse(false, problem[0]);

        GameController.setCurrentTurnUser(userOf(handler));
        GameController.setSelectedCivilizedUnit(worker);
        String message = request.isStation()
                ? sut.civilization.Controller.GameControllers.WorkerController.stationWorker()
                : sut.civilization.Controller.GameControllers.WorkerController.unstationWorker();
        GameController.setSelectedCivilizedUnit(null);
        boolean ok = worker.isStationed() == request.isStation();
        return new ActionResultResponse(ok, message);
    }

    /** Queues a technology research - same reuse pattern as unit training. */
    private ActionResultResponse handleResearch(ClientHandler handler,
                                                sut.civilization.Network.Messages.ResearchRequest request) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);

        User user = userOf(handler);
        GameController.setCurrentTurnUser(user);
        TownHall townHall = user.getNation().getTownHall();
        int queuedBefore = townHall == null ? 0 : townHall.getProductionQueue().size();
        String message = sut.civilization.Controller.GameControllers.TownHallController
                .researchTechnology(request.getTechName());
        boolean ok = townHall != null && townHall.getProductionQueue().size() > queuedBefore;
        return new ActionResultResponse(ok, message);
    }

    /** Queues the Town Hall upgrade - same reuse pattern as unit training. */
    private ActionResultResponse handleUpgradeTownHall(ClientHandler handler) {
        String turnProblem = checkTurn(handler);
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);

        User user = userOf(handler);
        GameController.setCurrentTurnUser(user);
        TownHall townHall = user.getNation().getTownHall();
        int queuedBefore = townHall == null ? 0 : townHall.getProductionQueue().size();
        String message = sut.civilization.Controller.GameControllers.TownHallController.upgradeTownHall();
        boolean ok = townHall != null && townHall.getProductionQueue().size() > queuedBefore;
        return new ActionResultResponse(ok, message);
    }

    // ── demo/cheat shortcuts for evaluation (P3-58, P3-48 rules) ─────────────

    /**
     * Host-only. Executed on the AUTHORITATIVE game and announced to every
     * player in the broadcast - a cheat is never a secret and never local.
     * These exist so evaluation scenarios (war, elimination, trades) can be
     * demoed in minutes instead of playing twenty legitimate turns.
     */
    private ActionResultResponse handleDebugAction(ClientHandler handler, DebugActionRequest request, List<String> notifications) {
        if (players.isEmpty() || players.get(0) != handler)
            return new ActionResultResponse(false, "Only the host can use demo actions.");

        User user = userOf(handler);
        Nation nation = user.getNation();
        String done;
        switch (request.getAction() == null ? "" : request.getAction()) {
            case DebugActionRequest.RESOURCES:
                // +500 of each, clamped by the storage capacity - so effectively
                // "fill the warehouses" (the cap itself stays a real game rule)
                nation.getFood().add(500);
                nation.getWood().add(500);
                nation.getStone().add(500);
                nation.getIron().add(500);
                done = "filled their storage with resources";
                break;
            case DebugActionRequest.REFRESH_AP:
                for (Unit unit : nation.getUnits()) unit.refreshAp();
                done = "refilled their units' AP";
                break;
            case DebugActionRequest.CLEAR_HOSTILES:
                for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
                    for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                        Game.instance.map[i][j].setEnemy(null);
                Game.instance.bearCalmUntilTurn = Game.instance.getTurn() + 999;
                done = "removed every bear, barbarian and camp from the map";
                break;
            case DebugActionRequest.WEAKEN_ENEMY_TH: {
                User enemy = firstOpponentOf(user);
                if (enemy == null || enemy.getNation().getTownHall() == null)
                    return new ActionResultResponse(false, "No enemy Town Hall to weaken.");
                enemy.getNation().getTownHall().setHp(15);
                done = "set " + enemy.getUsername() + "'s Town Hall HP to 15";
                break;
            }
            case DebugActionRequest.TELEPORT_TO_ENEMY: {
                CivilizedUnit soldier = null;
                for (Unit unit : nation.getUnits())
                    if (unit instanceof CivilizedUnit
                            && ((CivilizedUnit) unit).getCivilizedUnitType().isMilitary) {
                        soldier = (CivilizedUnit) unit;
                        break;
                    }
                if (soldier == null)
                    return new ActionResultResponse(false, "Train a military unit first - nothing to teleport.");
                User enemy = firstOpponentOf(user);
                if (enemy == null || enemy.getNation().getTownHall() == null)
                    return new ActionResultResponse(false, "No enemy Town Hall to teleport to.");
                Land thLand = enemy.getNation().getTownHall().getMainLand();
                Pair<Integer, Integer> spot = null;
                for (int k = 0; k < 6 && spot == null; k++) {
                    Pair<Integer, Integer> hex = LandController.getNeighborIndex(
                            new Pair<>(thLand.getI(), thLand.getJ()), k);
                    if (!Pair.isValid(hex)) continue;
                    Land tile = Game.instance.map[hex.x][hex.y];
                    if (tile.getCivilizedUnit() == null && tile.getEnemy() == null) spot = hex;
                }
                if (spot == null) return new ActionResultResponse(false, "No free hex next to the enemy Town Hall.");
                Pair<Integer, Integer> old = soldier.getLocation();
                if (Pair.isValid(old) && Game.instance.map[old.x][old.y].getCivilizedUnit() == soldier)
                    Game.instance.map[old.x][old.y].setCivilizedUnit(null);
                soldier.getLocation().x = spot.x;
                soldier.getLocation().y = spot.y;
                Game.instance.map[spot.x][spot.y].setCivilizedUnit(soldier);
                LandController.updateLandVisibility();
                done = "teleported their " + soldier.getCivilizedUnitType().name
                        + " next to " + enemy.getUsername() + "'s Town Hall";
                break;
            }
            default:
                return new ActionResultResponse(false, "Unknown demo action.");
        }
        notifications.add("DEMO CHEAT: " + user.getUsername() + " " + done + ".");
        return new ActionResultResponse(true, "Done - " + done + " (announced to everyone).");
    }

    /**
     * Chat-box cheat codes (bonus P3-48): typed as "/command", sent by the
     * client as a request, applied HERE on the authoritative game, host-only,
     * and always announced to every player - a cheat is never a secret.
     */
    private ActionResultResponse handleCheatCommand(ClientHandler handler,
                                                    sut.civilization.Network.Messages.CheatCommandRequest request,
                                                    List<String> notifications) {
        if (players.isEmpty() || players.get(0) != handler)
            return new ActionResultResponse(false, "Only the host can use cheat commands.");

        User user = userOf(handler);
        String[] words = (request.getText() == null ? "" : request.getText().trim().toLowerCase()).split("\\s+");
        String command = words[0];
        // an optional second word aims the cheat at another player, e.g.
        // "/greedisgood Bob" fills BOB's storage (default: the caller's own)
        User target = user;
        if (words.length > 1) {
            target = null;
            for (User p : Game.instance.getPlayersInGame())
                if (p.getUsername().equalsIgnoreCase(words[1])) target = p;
            if (target == null)
                return new ActionResultResponse(false, "There is no player called \"" + words[1] + "\".");
        }
        Nation nation = target.getNation();
        // "their storage" when aimed at yourself, "Bob's storage" otherwise
        String whose = target == user ? "their" : target.getUsername() + "'s";
        String done;
        switch (command) {
            case "/greedisgood":
                nation.getFood().add(500);
                nation.getWood().add(500);
                nation.getStone().add(500);
                nation.getIron().add(500);
                done = "filled " + whose + " storage with /greedisgood";
                break;
            case "/redbull":
                for (Unit unit : nation.getUnits()) unit.refreshAp();
                done = "refilled " + whose + " units' AP with /redbull";
                break;
            case "/marco":
                // full fog removal for the target nation only - their snapshots
                // now treat every hex as explored (vision circles stay honest)
                for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++)
                    for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++)
                        Game.instance.map[i][j].addSeerNation(nation);
                done = "removed " + whose + " fog of war with /marco";
                break;
            case "/smite": {
                // no name given -> the first opponent's Town Hall
                User enemy = target != user ? target : firstOpponentOf(user);
                if (enemy == null || enemy.getNation().getTownHall() == null)
                    return new ActionResultResponse(false, "No enemy Town Hall to smite.");
                Land thLand = enemy.getNation().getTownHall().getMainLand();
                // triggerEarthquake logs into turnNotifications for the SP flow -
                // pull those back out, the summary rides this broadcast instead
                int before = GameController.turnNotifications.size();
                String summary = sut.civilization.Controller.GameControllers.DisasterController
                        .triggerEarthquake(new Pair<>(thLand.getI(), thLand.getJ()));
                while (GameController.turnNotifications.size() > before)
                    GameController.turnNotifications.remove(GameController.turnNotifications.size() - 1);
                notifications.add(summary);
                done = "called an earthquake on " + enemy.getUsername() + "'s Town Hall with /smite";
                break;
            }
            case "/cavalry": {
                int converted = 0;
                for (Unit unit : new ArrayList<>(nation.getUnits())) {
                    if (!(unit instanceof CivilizedUnit)) continue;
                    CivilizedUnit worker = (CivilizedUnit) unit;
                    if (worker.getCivilizedUnitType() != CivilizedUnitType.WORKER || worker.isStationed()) continue;
                    Pair<Integer, Integer> loc = worker.getLocation();
                    if (!Pair.isValid(loc) || Game.instance.map[loc.x][loc.y].getCivilizedUnit() != worker) continue;
                    nation.getUnits().remove(worker);
                    CivilizedUnit cavalry = new CivilizedUnit(CivilizedUnitType.CAVALRY, nation,
                            new Pair<>(loc.x, loc.y));
                    Game.instance.map[loc.x][loc.y].setCivilizedUnit(cavalry);
                    nation.getUnits().add(cavalry);
                    converted++;
                }
                done = "turned " + converted + " of " + whose + " worker(s) into Cavalry with /cavalry";
                break;
            }
            case "/help":
                return new ActionResultResponse(false,
                        "Cheats (host only): /greedisgood /redbull /marco /smite /cavalry."
                                + " Add a name to aim one at a player, e.g. /greedisgood Bob");
            default:
                return new ActionResultResponse(false,
                        "Unknown cheat \"" + command + "\" - try /help");
        }
        notifications.add("CHEAT: " + user.getUsername() + " " + done + ".");
        return new ActionResultResponse(true, "Done - " + done + " (announced to everyone).");
    }

    /** First other player still in the game, or null. */
    private User firstOpponentOf(User user) {
        for (User other : Game.instance.getPlayersInGame())
            if (other != user && !Game.instance.isEliminated(other.getUsername())) return other;
        return null;
    }

    // ── phase 3 PvP: diplomacy (P3-38..40) ───────────────────────────────────

    /** The named opponent, or null - shared validation for every diplomacy request. */
    private User findOpponent(ClientHandler handler, String target) {
        User other = GameController.findPlayer(target);
        if (other == null || other.getUsername().equals(handler.getUsername())) return null;
        if (Game.instance.isEliminated(target)) return null;
        return other;
    }

    private ActionResultResponse handleDeclareWar(ClientHandler handler, DeclareWarRequest request, List<String> notifications) {
        String turnProblem = checkTurn(handler); // spec: an action inside your own turn
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        User other = findOpponent(handler, request.getTarget());
        if (other == null) return new ActionResultResponse(false, "There is no such player to declare war on.");

        String me = handler.getUsername(), them = other.getUsername();
        if (Game.instance.relationBetween(me, them) == Relation.ENEMY)
            return new ActionResultResponse(false, "You are already at war with " + them + ".");

        // declaring war also shreds an alliance instantly (the spec's "escalate" path)
        Game.instance.setRelation(me, them, Relation.ENEMY);
        Game.instance.getAllianceOffers().remove(me);
        Game.instance.getAllianceOffers().remove(them);
        // a war declaration is a global alarm for everyone in the game (P3-39)
        notifications.add(me + " has DECLARED WAR on " + them + "!");
        return new ActionResultResponse(true, "You are now at war with " + them + ".");
    }

    private ActionResultResponse handleOfferAlliance(ClientHandler handler, OfferAllianceRequest request) {
        User other = findOpponent(handler, request.getTarget());
        if (other == null) return new ActionResultResponse(false, "There is no such player to ally with.");

        String me = handler.getUsername(), them = other.getUsername();
        if (Game.instance.relationBetween(me, them) == Relation.ALLY)
            return new ActionResultResponse(false, "You are already allied with " + them + ".");
        // between enemies the same offer means PEACE (accept -> back to Neutral),
        // otherwise it is an alliance offer (accept -> Ally) - one flow, two words
        boolean atWar = Game.instance.relationBetween(me, them) == Relation.ENEMY;
        String word = atWar ? "PEACE" : "an ALLIANCE";

        Game.instance.getAllianceOffers().put(me, them);
        // tell the target directly - they answer from their Diplomacy panel
        for (ClientHandler h : players)
            if (h.getUsername().equals(them))
                h.send(new ErrorResponse(me + " offers you " + word + " - open Diplomacy to answer."));
        return new ActionResultResponse(true, (atWar ? "Peace" : "Alliance") + " offer sent to " + them + ".");
    }

    private ActionResultResponse handleAllianceReply(ClientHandler handler, AllianceReplyRequest reply, List<String> notifications) {
        String me = handler.getUsername();
        String proposer = reply.getFrom();
        if (!me.equals(Game.instance.getAllianceOffers().get(proposer)))
            return new ActionResultResponse(false, "There is no alliance offer from " + proposer + " waiting for you.");

        Game.instance.getAllianceOffers().remove(proposer);
        boolean atWar = Game.instance.relationBetween(me, proposer) == Relation.ENEMY;
        String kind = atWar ? "peace" : "alliance";
        if (!reply.isAccept()) {
            tell(proposer, me + " turned down your " + kind + " offer.");
            return new ActionResultResponse(true, "You turned down " + proposer + "'s " + kind + " offer.");
        }

        if (atWar) {
            // accepting between enemies ends the war: back to Neutral, and the
            // end of a war is global news just like its declaration (P3-39)
            Game.instance.setRelation(me, proposer, Relation.NEUTRAL);
            notifications.add(me + " and " + proposer + " have made PEACE.");
            return new ActionResultResponse(true, "You made peace with " + proposer + " - back to Neutral.");
        }
        Game.instance.setRelation(me, proposer, Relation.ALLY);
        // allies share their combined vision from the next snapshot on (P3-40)
        notifications.add(me + " and " + proposer + " are now ALLIES!");
        return new ActionResultResponse(true, "You are now allied with " + proposer + ".");
    }

    private ActionResultResponse handleBreakAlliance(ClientHandler handler, BreakAllianceRequest request, List<String> notifications) {
        User other = findOpponent(handler, request.getTarget());
        if (other == null) return new ActionResultResponse(false, "There is no such player.");

        String me = handler.getUsername(), them = other.getUsername();
        if (Game.instance.relationBetween(me, them) != Relation.ALLY)
            return new ActionResultResponse(false, "You are not allied with " + them + ".");

        // one-sided break, straight back to Neutral (P3-40)
        Game.instance.setRelation(me, them, Relation.NEUTRAL);
        notifications.add(me + " broke the alliance with " + them + ".");
        return new ActionResultResponse(true, "Alliance with " + them + " is over - back to Neutral.");
    }

    // ── phase 3 PvP: trade (P3-35..37) ───────────────────────────────────────

    private ActionResultResponse handleTradeOffer(ClientHandler handler, TradeOfferRequest request) {
        String turnProblem = checkTurn(handler); // turn-based game: offers go out in your turn
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
        User other = findOpponent(handler, request.getTo());
        if (other == null) return new ActionResultResponse(false, "There is no such player to trade with.");

        User me = userOf(handler);
        TradeOffer offer = new TradeOffer(me.getUsername(), other.getUsername(),
                Math.max(0, request.getGiveFood()), Math.max(0, request.getGiveWood()),
                Math.max(0, request.getGiveStone()), Math.max(0, request.getGiveIron()),
                Math.max(0, request.getWantFood()), Math.max(0, request.getWantWood()),
                Math.max(0, request.getWantStone()), Math.max(0, request.getWantIron()));
        if (offer.giveFood + offer.giveWood + offer.giveStone + offer.giveIron == 0)
            return new ActionResultResponse(false, "You have to offer at least one resource.");

        // the server independently confirms the sender really owns what they offer
        // (P3-36). Only resources actually being GIVEN are checked - a negative
        // food balance (starvation debt) must not block a wood-only offer
        Nation nation = me.getNation();
        if ((offer.giveFood > 0 && nation.getFood().getBalance() < offer.giveFood)
                || (offer.giveWood > 0 && nation.getWood().getBalance() < offer.giveWood)
                || (offer.giveStone > 0 && nation.getStone().getBalance() < offer.giveStone)
                || (offer.giveIron > 0 && nation.getIron().getBalance() < offer.giveIron))
            return new ActionResultResponse(false, "You do not have the resources you are offering.");

        // lock them: taken out of the balance now so they can't be spent while waiting
        nation.getFood().add(-offer.giveFood);
        nation.getWood().add(-offer.giveWood);
        nation.getStone().add(-offer.giveStone);
        nation.getIron().add(-offer.giveIron);
        Game.instance.getTradeOffers().add(offer);

        for (ClientHandler h : players)
            if (h.getUsername().equals(offer.to))
                h.send(new ErrorResponse(offer.from + " sent you a TRADE offer ("
                        + offer.describeGive() + " for " + offer.describeWant() + ") - open Trade to answer."));
        return new ActionResultResponse(true, "Trade offer sent to " + offer.to
                + " (offered resources are locked until they answer).");
    }

    private ActionResultResponse handleTradeReply(ClientHandler handler, TradeReplyRequest reply) {
        TradeOffer offer = Game.instance.findTradeOffer(reply.getOfferId());
        if (offer == null || !offer.isPending())
            return new ActionResultResponse(false, "That trade offer no longer exists.");

        String me = handler.getUsername();
        User sender = GameController.findPlayer(offer.from);
        User target = GameController.findPlayer(offer.to);
        if (sender == null || target == null)
            return new ActionResultResponse(false, "A player of that trade is gone.");

        // the sender may cancel their own waiting offer (in their later turns)
        if (me.equals(offer.from)) {
            if (reply.isAccept())
                return new ActionResultResponse(false, "You cannot accept your own offer.");
            String turnProblem = checkTurn(handler);
            if (turnProblem != null) return new ActionResultResponse(false, turnProblem);
            offer.setStatus(TradeOffer.CANCELLED);
            GameController.refundLockedResources(sender.getNation(), offer);
            tell(offer.to, offer.from + " CANCELLED their trade offer (" + offer.describeGive() + ").");
            return new ActionResultResponse(true, "Offer cancelled - your locked resources are back.");
        }

        if (!me.equals(offer.to))
            return new ActionResultResponse(false, "That trade offer is not addressed to you.");
        String turnProblem = checkTurn(handler); // the inbox is answered in your own turn
        if (turnProblem != null) return new ActionResultResponse(false, turnProblem);

        if (!reply.isAccept()) {
            offer.setStatus(TradeOffer.REJECTED);
            GameController.refundLockedResources(sender.getNation(), offer);
            // the sender must hear the verdict too, not just watch their inbox (P3-54)
            tell(offer.from, offer.to + " REJECTED your trade offer - your locked "
                    + offer.describeGive() + " are back.");
            return new ActionResultResponse(true, "Offer rejected - " + offer.from + " gets their resources back.");
        }

        // accept: the server checks the acceptor really has the asked resources
        // (P3-37) - again only the ones actually being asked for
        Nation mine = target.getNation();
        if ((offer.wantFood > 0 && mine.getFood().getBalance() < offer.wantFood)
                || (offer.wantWood > 0 && mine.getWood().getBalance() < offer.wantWood)
                || (offer.wantStone > 0 && mine.getStone().getBalance() < offer.wantStone)
                || (offer.wantIron > 0 && mine.getIron().getBalance() < offer.wantIron))
            return new ActionResultResponse(false, "You do not have the resources this offer asks for.");

        mine.getFood().add(-offer.wantFood + offer.giveFood);
        mine.getWood().add(-offer.wantWood + offer.giveWood);
        mine.getStone().add(-offer.wantStone + offer.giveStone);
        mine.getIron().add(-offer.wantIron + offer.giveIron);
        // the sender's give-part was already locked out of their balance
        sender.getNation().getFood().add(offer.wantFood);
        sender.getNation().getWood().add(offer.wantWood);
        sender.getNation().getStone().add(offer.wantStone);
        sender.getNation().getIron().add(offer.wantIron);
        offer.setStatus(TradeOffer.ACCEPTED);
        tell(offer.from, offer.to + " ACCEPTED your trade offer - you received " + offer.describeWant() + ".");
        return new ActionResultResponse(true, "Trade completed with " + offer.from + ".");
    }

    // ── state broadcast with per-player fog of war ────────────────────────────

    private void broadcastState(List<String> notifications) {
        for (ClientHandler h : players) {
            try {
                String state = buildSnapshotFor(h.getUsername());
                h.send(new GameStateBroadcast(state, currentPlayerName(), Game.instance.getTurn(),
                        notificationsFor(h.getUsername(), notifications)));
            } catch (Exception e) {
                System.out.println("Snapshot for " + h.getUsername() + " failed: " + e.getMessage());
            }
        }
    }

    /**
     * A note tagged "@username::" is one player's private news (their unit was
     * attacked, their training finished, their starvation) - fog of war applies
     * to news too, so only its owner receives it. Untagged notes (seasons,
     * disasters, war declarations, eliminations) go to everyone.
     */
    private List<String> notificationsFor(String username, List<String> notifications) {
        List<String> visible = new ArrayList<>();
        for (String note : notifications) {
            if (note.startsWith("@@")) {
                // place-tagged news (quake, bear den): only for players whose map
                // already knows that hex - no phantom reports about fogged spots
                int sep = note.indexOf("::");
                if (sep > 2) {
                    try {
                        String[] xy = note.substring(2, sep).split(",");
                        if (hasExplored(username, Integer.parseInt(xy[0]), Integer.parseInt(xy[1])))
                            visible.add(note.substring(sep + 2));
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
            }
            if (note.startsWith("@")) {
                int sep = note.indexOf("::");
                if (sep > 1) {
                    if (note.substring(1, sep).equals(username)) visible.add(note.substring(sep + 2));
                    continue;
                }
            }
            visible.add(note);
        }
        return visible;
    }

    /** Has this player (or an ally - shared vision) ever explored that hex? */
    private boolean hasExplored(String username, int x, int y) {
        if (!inBounds(x, y)) return false;
        User user = GameController.findPlayer(username);
        if (user == null) return false;
        Land tile = Game.instance.map[x][y];
        if (tile.getSeerNations().contains(user.getNation())) return true;
        for (User other : Game.instance.getPlayersInGame())
            if (Game.instance.relationBetween(username, other.getUsername()) == Relation.ALLY
                    && tile.getSeerNations().contains(other.getNation())) return true;
        return false;
    }

    /** A direct heads-up popup to one player (trade answered, alliance turned down...). */
    private void tell(String username, String text) {
        for (ClientHandler h : players)
            if (h.getUsername().equals(username)) h.send(new ErrorResponse(text));
    }

    /**
     * Deep-copies the game, recomputes visibility for ONE receiving player and
     * strips everything their fog hides - the packet physically lacks the
     * hidden data, as the spec demands (sending the full map is forbidden even
     * if the UI would not draw it).
     */
    String buildSnapshotFor(String username) throws Exception {
        Game copy = deepCopy(Game.instance);
        Nation viewer = null;
        for (User u : copy.getPlayersInGame())
            if (u.getUsername().equals(username)) viewer = u.getNation();
        if (viewer == null) throw new IllegalStateException("no nation for " + username);

        // allies share their combined vision (P3-40): their eyes count as ours
        List<Nation> friendly = new ArrayList<>();
        friendly.add(viewer);
        for (User u : copy.getPlayersInGame())
            if (copy.relationBetween(username, u.getUsername()) == Relation.ALLY)
                friendly.add(u.getNation());

        // recompute visibility from scratch for this viewer (+ allies):
        // 0 = never seen, 1 = seen before (shadow), 2 = currently in vision range
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                boolean seen = false;
                for (Nation nation : friendly)
                    if (copy.map[i][j].getSeerNations().contains(nation)) seen = true;
                copy.map[i][j].setVisibility(seen ? 1 : 0);
            }
        }
        for (Nation nation : friendly) {
            for (Pair<Integer, Integer> hex : nation.getTerritory())
                if (Pair.isValid(hex)) copy.map[hex.x][hex.y].setVisibility(2);
            for (Unit unit : nation.getUnits()) {
                int radius = 1;
                if (unit instanceof CivilizedUnit)
                    radius = ((CivilizedUnit) unit).getCivilizedUnitType().visionRadius;
                revealOnCopy(copy, unit.getLocation(), radius);
            }
        }

        // strip what the fog hides
        for (int i = 0; i < Consts.MAP_SIZE.amount.x; i++) {
            for (int j = 0; j < Consts.MAP_SIZE.amount.y; j++) {
                Land tile = copy.map[i][j];
                if (tile.getVisibility() == 0) {
                    // never seen: no units, no enemies, no buildings, no loot, no
                    // resources - and no TERRAIN either (a tooltip could otherwise
                    // read the land type off a pitch-black hex). Every unseen hex
                    // travels as an identical plain, so nothing distinguishes them
                    tile.setCivilizedUnit(null);
                    tile.setEnemy(null);
                    tile.setImprovement(null);
                    tile.setRuin(null);
                    tile.setResource(null);
                    tile.setIronResource(null);
                    tile.setHasIron(false);
                    tile.setLandType(LandType.PLAIN);
                    tile.setLandFeature(null);
                    tile.setHasRoad(false);
                    tile.setVolcano(false);
                } else if (tile.getVisibility() == 1) {
                    // seen before: terrain and improvements stay, live intel goes
                    tile.setCivilizedUnit(null);
                    tile.setEnemy(null);
                }
                // "who has seen this hex" is intel about the OTHER players - the
                // packet keeps only the viewer's own entries (and their allies')
                tile.getSeerNations().retainAll(friendly);
                tile.getExplorerSeenNations().retainAll(friendly);
            }
        }

        // the object graph itself must not leak either: an opponent's Nation
        // carries its unit list, territory and town hall. Blank everything the
        // viewer's fog hides - units still visible on revealed tiles survive,
        // because tiles hold their own reference to those units. Allied nations
        // are NOT stripped: shared vision means their graph is open to us
        for (User u : copy.getPlayersInGame()) {
            Nation other = u.getNation();
            if (friendly.contains(other)) continue;
            other.getUnits().clear();
            other.getTerritory().removeIf(hex ->
                    !Pair.isValid(hex) || copy.map[hex.x][hex.y].getVisibility() == 0);
            if (other.getTownHall() != null) {
                Land thLand = other.getTownHall().getMainLand();
                if (copy.map[thLand.getI()][thLand.getJ()].getVisibility() == 0)
                    other.setTownHall(null);
            }
        }

        // nothing about the other players' ECONOMY or the server's hidden state
        // may travel either:
        // - every other nation's resource balances are zeroed (shared vision with
        //   an ally is not open account books)
        // - trade/alliance offers between OTHER players are none of the viewer's business
        // - a fogged bear den stays secret, and the server's RNG state never leaves
        //   (a client that knows the RNG could predict every future die roll)
        for (User u : copy.getPlayersInGame()) {
            Nation other = u.getNation();
            if (other == viewer) continue;
            other.getFood().add(-other.getFood().getBalance());
            other.getWood().add(-other.getWood().getBalance());
            other.getStone().add(-other.getStone().getBalance());
            other.getIron().add(-other.getIron().getBalance());
        }
        copy.getTradeOffers().removeIf(offer ->
                !offer.from.equals(username) && !offer.to.equals(username));
        copy.getAllianceOffers().entrySet().removeIf(e ->
                !e.getKey().equals(username) && !e.getValue().equals(username));
        if (copy.bearDen != null && copy.map[copy.bearDen.x][copy.bearDen.y].getVisibility() == 0)
            copy.bearDen = null;
        copy.scrubRng();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(copy);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /** BFS reveal on the copied map (Game.instance must stay untouched here). */
    private void revealOnCopy(Game copy, Pair<Integer, Integer> center, int radius) {
        ArrayList<Pair<Integer, Integer>> visited = new ArrayList<>();
        ArrayList<Pair<Integer, Integer>> frontier = new ArrayList<>();
        frontier.add(center);
        visited.add(center);
        for (int r = 0; r <= radius; r++) {
            ArrayList<Pair<Integer, Integer>> next = new ArrayList<>();
            for (Pair<Integer, Integer> hex : frontier) {
                if (Pair.isValid(hex)) copy.map[hex.x][hex.y].setVisibility(2);
                if (r < radius) {
                    for (int k = 0; k < 6; k++) {
                        Pair<Integer, Integer> neighbor = LandController.getNeighborIndex(hex, k);
                        if (Pair.isValid(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            next.add(neighbor);
                        }
                    }
                }
            }
            frontier = next;
        }
    }

    private static Game deepCopy(Game game) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(game);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Game) in.readObject();
        }
    }

    private User userOf(ClientHandler handler) {
        for (User u : Game.instance.getPlayersInGame())
            if (u.getUsername().equals(handler.getUsername())) return u;
        throw new IllegalStateException("no user for " + handler.getUsername());
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < Consts.MAP_SIZE.amount.x && y >= 0 && y < Consts.MAP_SIZE.amount.y;
    }
}

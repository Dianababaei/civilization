package sut.civilization.Controller.GameControllers;

import sut.civilization.Model.Classes.Game;
import sut.civilization.Model.Classes.LandGraphical;
import sut.civilization.Model.ModulEnums.Season;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Phase 2 save/load (P2-63..67). Plain Java serialization of the whole Game object:
 * 3 manual slots + 1 autosave slot, each file carries a version + metadata header.
 * Writes are atomic (temp file first, then replace) so a crash mid-save can never
 * corrupt the previous good file.
 */
public class SaveController extends GameController {

    public static final int SAVE_VERSION = 1;
    // not final: the headless test driver points this at a scratch folder so its
    // saves/autosaves never overwrite the player's real files
    public static String saveDirectory = "saves";
    public static final String[] SLOTS = {"slot1", "slot2", "slot3"};
    public static final String AUTOSAVE_SLOT = "autosave";

    /** What actually goes into a .sav file: metadata in front, then the game itself. */
    public static class SaveData implements Serializable {
        public int version;
        public String name;
        public long savedAtMillis;
        public int turn;
        public String season;
        public Game game;
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    public static String saveGame(String slot, String name) {
        try {
            File dir = new File(saveDirectory);
            dir.mkdirs();

            SaveData data = new SaveData();
            data.version = SAVE_VERSION;
            data.name = (name == null || name.trim().isEmpty()) ? slot : name.trim();
            data.savedAtMillis = System.currentTimeMillis();
            data.turn = Game.instance.getTurn();
            data.season = Season.forTurn(data.turn).name;
            data.game = Game.instance;

            // atomic write: serialize into a temp file, then swap it in
            File tmp = new File(dir, slot + ".tmp");
            File real = new File(dir, slot + ".sav");
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmp))) {
                out.writeObject(data);
            }
            java.nio.file.Files.move(tmp.toPath(), real.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "Saved \"" + data.name + "\" to " + slot + " (turn " + data.turn + ", " + data.season + ").";
        } catch (Exception e) {
            return "Save FAILED (" + e.getMessage() + ") - the previous save file is untouched!";
        }
    }

    /** Autosave, called at the very end of every turn. Returns null on success. */
    public static String autosave() {
        String result = saveGame(AUTOSAVE_SLOT, "autosave");
        return result.startsWith("Save FAILED") ? "Autosave failed - last good autosave kept!" : null;
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    /** Reads a slot's contents without applying it; null if empty or unreadable. */
    public static SaveData peek(String slot) {
        File file = new File(saveDirectory, slot + ".sav");
        if (!file.exists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (SaveData) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** One-line label for the slot list UI, e.g. "slot1 - camp3, turn 12, Winter, 2026-08-18 09:41". */
    public static String describeSlot(String slot) {
        SaveData data = peek(slot);
        if (data == null) return slot + " - empty";
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(data.savedAtMillis));
        return slot + " - " + data.name + ", turn " + data.turn + ", " + data.season + ", " + when;
    }

    public static String loadGame(String slot) {
        File file = new File(saveDirectory, slot + ".sav");
        if (!file.exists()) return "Slot " + slot + " is empty!";

        SaveData data;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            data = (SaveData) in.readObject();
        } catch (Exception e) {
            return "Load FAILED: the file is corrupted (" + e.getMessage() + ")!";
        }
        if (data.version != SAVE_VERSION)
            return "Load FAILED: save version " + data.version + " is not compatible with this game (needs " + SAVE_VERSION + ")!";
        if (data.game == null || data.game.map == null)
            return "Load FAILED: the file has no game state in it!";

        // swap the loaded game in, keeping the current window/scene plumbing
        Game loaded = data.game;
        loaded.adoptWindowFrom(Game.instance);
        Game.instance = loaded;

        // the distance/path cache is transient - rebuild it from the loaded map
        loaded.dist = new int[225][225];
        loaded.path = new String[225][225];
        LandController.initializeDistances();
        LandController.updateDistances();

        // reset every bit of static per-session state that pointed at the old game
        setCurrentTurnUser(loaded.getPlayersInGame().get(0));
        setSelectedCivilizedUnit(null);
        turnNotifications.clear();
        DisasterController.lastQuakeArea = null;
        DisasterController.lastQuakeCenter = null;
        LandGraphical.quakeFlashTiles.clear();
        TechnologyController.updateNextAvailableTechnologies();
        CityController.updateAffordableLands();

        return "Loaded \"" + data.name + "\" (turn " + data.turn + ", " + data.season + ").";
    }
}

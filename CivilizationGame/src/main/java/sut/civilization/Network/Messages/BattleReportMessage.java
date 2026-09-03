package sut.civilization.Network.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: one resolved battle with all its dice, so the client can
 * show the same dice dialog as single-player. The attacker gets it right away;
 * the defender gets it queued as a "war report" delivered when their own turn
 * starts (P3-34 - never a silent battle).
 */
public class BattleReportMessage extends Message {
    public static final String TYPE = "BattleReportMessage";

    private final String title;
    private final int[] attackerDice;
    private final int[] defenderDice;
    private final List<String> lines;
    private final String summary;

    public BattleReportMessage(String title, int[] attackerDice, int[] defenderDice,
                               List<String> lines, String summary) {
        super(TYPE);
        this.title = title;
        this.attackerDice = attackerDice;
        this.defenderDice = defenderDice;
        this.lines = new ArrayList<>(lines);
        this.summary = summary;
    }

    public String getTitle() {
        return title;
    }

    public int[] getAttackerDice() {
        return attackerDice;
    }

    public int[] getDefenderDice() {
        return defenderDice;
    }

    public List<String> getLines() {
        return lines;
    }

    public String getSummary() {
        return summary;
    }
}

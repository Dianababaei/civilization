package sut.civilization.Model.ModulEnums;

import sut.civilization.Civilization;

import javax.swing.*;

public enum UnitActions {
    MOVE(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Move.png")), "Move"),
    DELETE(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Delete.png")), "Delete"),
    WAKE(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Wake.png")), "Wake"),
    SLEEP(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Sleep.png")), "Sleep"),
    // Displayed as "Station/Unstation Worker": stations a Worker in the improvement on its
    // current tile to begin production, or removes it if already stationed (WorkerController).
    REPAIR(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Repair.png")), "Station/Unstation Worker"),
    ATTACK(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Attack.png")), "Attack"),
    RANGED_ATTACK(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Ranged Attack.png")), "Ranged Attack"),
    FORTIFY_UNTIL_HEAL(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Heal.png")), "Fortify Until Healed"),
    // Displayed as "Expand Border": this is the Border Expander's claim-territory action
    // (TownHallController.expandBorder), not city founding.
    FOUND_CITY(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Found City.png")), "Expand Border"),
    FORTIFY(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Fortify.png")), "Fortify"),
    BUILD_IMPROVEMENT(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Build Improvement.png")), "Build Improvement"),
    REMOVE_FEATURE(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Remove Feature.png")), "Remove Feature"),
    ALERT(new ImageIcon(Civilization.class.getResource("/sut/civilization/Images/unitActions/Alert.png")), "Alert")
    ;

    public ImageIcon icon;
    /** Human-readable label shown in tooltips, instead of the raw enum constant name. */
    public final String label;

    UnitActions(ImageIcon icon, String label) {
        this.icon = icon;
        this.label = label;
    }

}

package sut.civilization.Model.ModulEnums;

public enum ResourceType {
    FOOD("Food"),
    WOOD("Wood"),
    STONE("Stone"),
    IRON("Iron");

    public final String name;

    ResourceType(String name) {
        this.name = name;
    }
}

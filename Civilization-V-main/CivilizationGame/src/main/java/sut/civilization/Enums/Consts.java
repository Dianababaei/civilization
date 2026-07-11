package sut.civilization.Enums;

import sut.civilization.Model.Classes.Pair;

public enum Consts {

    MAP_SIZE(new Pair<>(15, 15)),
    SCREEN_SIZE(new Pair<>(768, 1366))
    ;

    public final Pair<Integer,Integer> amount;

    Consts(Pair<Integer,Integer> amount){
        this.amount = amount;
    }
}

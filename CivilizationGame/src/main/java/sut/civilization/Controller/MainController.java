package sut.civilization.Controller;

import sut.civilization.Model.Classes.Game;

public class MainController extends Controller{

    public void exitGame(){
        Game.instance.getMainWindow().dispose();
    }
}

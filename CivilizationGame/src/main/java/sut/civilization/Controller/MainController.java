package sut.civilization.Controller;

import sut.civilization.Model.Classes.Game;

public class MainController extends Controller{

    public void exitGame(){
        Game.instance.getMainWindow().dispose();
        // dispose alone leaves the JVM running (network/audio threads are not
        // daemons): the process lingered invisibly, its sockets stayed open, so
        // the server never noticed the player left. Exit for real - sockets
        // close and the server reacts immediately
        System.exit(0);
    }
}

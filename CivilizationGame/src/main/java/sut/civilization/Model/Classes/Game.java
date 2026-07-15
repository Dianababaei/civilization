package sut.civilization.Model.Classes;

import sut.civilization.Enums.Menus;

import javax.swing.*;
import java.awt.CardLayout;
import java.util.ArrayList;


public class Game {
    public transient static Game instance = new Game();

    public Land[][] map;
    public int[][] dist = new int[225][225];
    public String[][] path = new String[225][225];
    private ArrayList<User> users = new ArrayList<>();
    private transient JFrame mainWindow;
    private transient CardLayout cardLayout;
    private transient JPanel screenContainer;
    private transient SceneFactory sceneFactory;
    private User loggedInUser;
    private int turn = 1;
    // a number between 0 and players count in order to know witch user have to play
    private int subTurn = 0;
    private ArrayList<User> playersInGame = new ArrayList<>();

    // gives back the JPanel for a screen, set up once by the app entry point
    public interface SceneFactory {
        JPanel createScreen(Menus menu);
    }

    public JFrame getMainWindow() {
        return mainWindow;
    }

    // sets up the main window + CardLayout container, only called once when app starts
    public void initWindow(JFrame mainWindow, CardLayout cardLayout, JPanel screenContainer, SceneFactory sceneFactory) {
        this.mainWindow = mainWindow;
        this.cardLayout = cardLayout;
        this.screenContainer = screenContainer;
        this.sceneFactory = sceneFactory;
    }

    public int getSubTurn() {
        return subTurn;
    }

    public void setSubTurn(int subTurn) {
        Game.instance.subTurn = subTurn;
    }

    public int getTurn() {
        return turn;
    }

    public void setTurn(int turn) {
        Game.instance.turn = turn;
    }

    public ArrayList<User> getPlayersInGame() {
        return playersInGame;
    }

    public void setPlayersInGame(ArrayList<User> playersInGame) {
        Game.instance.playersInGame = playersInGame;
    }

    public ArrayList<User> getUsers() {
        return users;
    }

    public void setUsers(ArrayList<User> users) {
        Game.instance.users = users;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    public void setLoggedInUser(User user) {
        Game.instance.loggedInUser = user;
    }

    public void setMap(Land[][] map) {
        Game.instance.map = map;
    }

    public void addUser(User user) {
        Game.instance.users.add(user);
    }

    // switches to the given screen, rebuilds it fresh every time (this is basically copying
    // how the old FXML version reloaded the scene root on every switch)
    public void changeScene(Menus menu) {
        JPanel screen = sceneFactory.createScreen(menu);
        String cardName = menu.name();
        screenContainer.add(screen, cardName);
        cardLayout.show(screenContainer, cardName);
        screenContainer.revalidate();
        screenContainer.repaint();
        screen.requestFocusInWindow();
    }
}

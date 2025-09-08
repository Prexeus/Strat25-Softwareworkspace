package com.example;

import com.example.service.GameService;
import com.example.service.NodeMode;
import com.example.view.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class Starter extends Application {

    private GameService gameService;
    private SceneManager sceneManager;

    @Override
    public void start(Stage primaryStage) {
        gameService = new GameService();
        sceneManager = new SceneManager(primaryStage, gameService);

        // Default-Mode zentral setzen: Host -> startet NetInputServer
        gameService.setNodeMode(NodeMode.HOST);

        sceneManager.showLauncher(); // erstes Fenster: Neu/Load/Backup
        primaryStage.show();
    }

    @Override
    public void stop() {
        try {
            // Laufende Game-Logik beenden
            gameService.stopGame();
        } catch (Exception ignored) {}

        // Netzwerk/Discovery sauber schließen
        try {
            gameService.shutdown();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}

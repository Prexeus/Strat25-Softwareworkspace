package com.example;

import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class Starter extends Application {

    @Override
    public void start(Stage primaryStage) {
        GameService gameService = new GameService();

        SceneManager sceneManager = new SceneManager(primaryStage, gameService);
        sceneManager.showLauncher(); // erstes Fenster: Neu/Load/Backup

        primaryStage.setOnCloseRequest(e -> {
            try {
                gameService.stopGame();
                // Optional: final save
                // if (gameService.getGame() != null) gameService.saveGame();
            } catch (Exception ignored) {}
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
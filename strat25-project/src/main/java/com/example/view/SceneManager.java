package com.example.view;

import com.example.service.GameService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.lang.reflect.Constructor;

public class SceneManager {

    private final Stage stage;
    private final GameService gameService;

    public SceneManager(Stage stage, GameService gameService) {
        this.stage = stage;
        this.gameService = gameService;
    }

    public void showLauncher() {
        setScene("/com/example/view/LauncherView.fxml", "Game Launcher");
    }

    public void showGame() {
        String title = (gameService.getGame() != null) ? "Game - " + gameService.getGame().getName() : "Game";
        setScene("/com/example/view/GameView.fxml", title);
    }

    private void setScene(String fxmlPath, String title) {
        try {
            Parent root = loadView(fxmlPath);
            Scene scene = new Scene(root);
            // optional: Stylesheet
            // scene.getStylesheets().add(getClass().getResource("/com/example/view/app.css").toExternalForm());
            stage.setTitle(title);
            stage.setScene(scene);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load view: " + fxmlPath, e);
        }
    }

    private Parent loadView(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(makeControllerFactory());
        return loader.load();
    }

    private Callback<Class<?>, Object> makeControllerFactory() {
        return type -> {
            // Bevorzugt Konstruktoren (GameService, SceneManager)
            try {
                for (Constructor<?> c : type.getConstructors()) {
                    Class<?>[] params = c.getParameterTypes();
                    if (params.length == 2 &&
                        params[0].isAssignableFrom(GameService.class) &&
                        params[1].isAssignableFrom(SceneManager.class)) {
                        return c.newInstance(gameService, this);
                    }
                }
                // Fallback: no-arg
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate controller: " + type, e);
            }
        };
    }
}

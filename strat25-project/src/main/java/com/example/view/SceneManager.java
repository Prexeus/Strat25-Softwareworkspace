package com.example.view;

import com.example.controller.ControlController;
import com.example.controller.ResultController;
import com.example.service.GameService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;

public class SceneManager {

    // Hauptfenster
    private final Stage stage;
    private final GameService gameService;

    // Zusatzfenster
    private Stage controlStage; // Steuerfenster
    private Stage resultStage; // Result-/Leinwandfenster

    // Referenzen auf Controller (für spätere Steuerung)
    private ControlController controlController;
    private ResultController resultController;

    public SceneManager(Stage stage, GameService gameService) {
        this.stage = stage;
        this.gameService = gameService;
    }

    // --------- bestehende API ---------

    public void showLauncher() {
        setSceneOn(stage, "/com/example/view/LauncherView.fxml", "Game Launcher");
        stage.show();
    }

    public void showGame() {
        String title = (gameService.getGame() != null)
                ? "Game - " + gameService.getGame().getName()
                : "Game";
        setSceneOn(stage, "/com/example/view/GameView.fxml", title);
        stage.show();
    }

    // --------- neue API: Steuer-/Resultfenster ---------

    /** Öffnet (oder fokussiert) das Steuerfenster. */
    public void showControlWindow() {
        if (controlStage == null) {
            try {
                FXMLLoader loader = makeLoader("/com/example/view/ControlView.fxml");
                Parent root = loader.load();
                controlController = loader.getController();

                controlStage = new Stage();
                controlStage.initOwner(stage);
                controlStage.setTitle("Steuerfenster");
                controlStage.setScene(new Scene(root));

                // Beim Schließen nur verstecken, nicht zerstören
                controlStage.setOnCloseRequest(ev -> {
                    controlStage.hide();
                    ev.consume();
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to load ControlView.fxml", e);
            }
        }
        if (!controlStage.isShowing())
            controlStage.show();
        controlStage.toFront();
        controlStage.requestFocus();
    }

    /** Öffnet (oder fokussiert) das Result-/Leinwandfenster. */
    public void showResultWindow() {
        if (resultStage == null) {
            try {
                FXMLLoader loader = makeLoader("/com/example/view/ResultView.fxml");
                Parent root = loader.load();
                resultController = loader.getController();

                resultStage = new Stage();
                resultStage.initOwner(stage);
                resultStage.setTitle("Resultfenster");
                resultStage.setScene(new Scene(root));

                resultStage.setWidth(900);   // Anfangsbreite
                resultStage.setHeight(600);  // Anfangshöhe

                // Vollbild-Exit per ESC erlauben + Hinweistext
                resultStage.setFullScreenExitHint("ESC zum Vollbild verlassen");
                resultStage.setFullScreenExitKeyCombination(new KeyCodeCombination(KeyCode.ESCAPE));

                // Zusätzliche Absicherung per Event-Filter:
                resultStage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    if (ev.getCode() == KeyCode.ESCAPE && resultStage.isFullScreen()) {
                        resultStage.setFullScreen(false);
                        ev.consume();
                    }
                });

                resultStage.setOnCloseRequest(ev -> {
                    resultStage.hide();
                    ev.consume();
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to load ResultView.fxml", e);
            }
        }
        if (!resultStage.isShowing())
            resultStage.show();
        resultStage.toFront();
        resultStage.requestFocus();
    }

    /** Vollbild für das Resultfenster umschalten (öffnet es bei Bedarf). */
    public void toggleResultFullscreen() {
        showResultWindow();
        resultStage.setFullScreen(!resultStage.isFullScreen());
    }

    /** Resultfenster ausblenden (falls sichtbar). */
    public void hideResultWindow() {
        if (resultStage != null && resultStage.isShowing()) {
            resultStage.hide();
        }
    }

    public boolean isResultShowing() {
        return resultStage != null && resultStage.isShowing();
    }

    public ControlController getControlController() {
        return controlController;
    }

    public ResultController getResultController() {
        return resultController;
    }

    /** Vom Steuerfenster aufgerufen: Auswahl an das Resultfenster pushen. */
    public void updateResultVisibility(java.util.Set<String> categoryNames, boolean showEndscore) {
        // Fenster bei Bedarf öffnen
        if (resultStage == null || !resultStage.isShowing()) {
            showResultWindow();
        }
        if (resultController != null) {
            resultController.applyVisibility(categoryNames, showEndscore);
        }
    }

    // --------- interne Helfer ---------

    private void setSceneOn(Stage target, String fxmlPath, String title) {
        try {
            Parent root = loadView(fxmlPath);
            Scene scene = new Scene(root);
            target.setTitle(title);
            target.setScene(scene);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load view: " + fxmlPath, e);
        }
    }

    private Parent loadView(String fxmlPath) throws IOException {
        FXMLLoader loader = makeLoader(fxmlPath);
        return loader.load();
    }

    private FXMLLoader makeLoader(String fxmlPath) {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new IllegalArgumentException("FXML not found on classpath: " + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(makeControllerFactory());
        return loader;
    }

    private Callback<Class<?>, Object> makeControllerFactory() {
        return type -> {
            // Bevorzugt Konstruktor (GameService, SceneManager)
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

    public void minimizeControlWindow() {
        if (controlStage == null)
            showControlWindow();
        if (controlStage != null)
            controlStage.setIconified(true);
    }

    public void minimizeResultWindow() {
        if (resultStage == null)
            showResultWindow();
        if (resultStage != null) {
            // aus dem Vollbild raus, bevor wir minimieren
            if (resultStage.isFullScreen())
                resultStage.setFullScreen(false);
            resultStage.setIconified(true);
        }
    }
}

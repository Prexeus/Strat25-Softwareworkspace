package com.example.controller;

import com.example.model.Family;
import com.example.model.Game;
import com.example.model.GameTime;
import com.example.model.Team;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class GameController {

    private final GameService gameService;
    private final SceneManager sceneManager;

    private Timeline uiTicker;

    public GameController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    // --- right column controls
    @FXML private Label gameNameLabel;
    @FXML private Label timeLabel;
    @FXML private Label speedLabel;
    @FXML private TextField speedField;
    @FXML private Button applySpeedBtn;

    @FXML private Button startBtn;
    @FXML private Button pauseBtn;
    @FXML private Button resumeBtn;
    @FXML private Button stopBtn;

    // prestige table
    @FXML private TableView<FamilyPrestigeRow> prestigeTable;
    @FXML private TableColumn<FamilyPrestigeRow, String> familyCol;
    @FXML private TableColumn<FamilyPrestigeRow, Number> prestigeCol;

    @FXML
    private void initialize() {
        Game g = gameService.getGame();
        if (g != null) {
            gameNameLabel.setText(g.getName());
            timeLabel.setText(formatSecondsFloor(g.getGameTime().getScaledSeconds()));
            speedLabel.setText(speedText(g.getGameTime().getGameSpeed()));
            // IMPORTANT: do not pre-fill speedField → keep placeholder "1.0"
            speedField.setPromptText("1.0");
            speedField.clear();
        }

        // buttons
        startBtn.setOnAction(e -> gameService.startGame());
        pauseBtn.setOnAction(e -> gameService.pauseGame());
        resumeBtn.setOnAction(e -> gameService.resumeGame());
        stopBtn.setOnAction(e -> {
            gameService.stopGame();
            sceneManager.showLauncher();
            if (uiTicker != null) uiTicker.stop();
        });

        // apply speed from text field (Enter or button)
        applySpeedBtn.setOnAction(e -> applySpeedFromField());
        speedField.setOnAction(e -> applySpeedFromField());

        // table columns
        familyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFamilyName()));
        prestigeCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getPrestigeSum()));
        prestigeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.2f", item.doubleValue()));
            }
        });

        // fill table once
        refreshPrestigeTable();

        // UI ticker (once per second)
        if (uiTicker != null) uiTicker.stop();
        uiTicker = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            Game game = gameService.getGame();
            if (game != null) {
                timeLabel.setText(formatSecondsFloor(game.getGameTime().getScaledSeconds()));
                speedLabel.setText(speedText(game.getGameTime().getGameSpeed()));
                // Do NOT auto-fill speedField (keep user's placeholder/typed value)
                refreshPrestigeTable();
            }
        }));
        uiTicker.setCycleCount(Timeline.INDEFINITE);
        uiTicker.play();
    }

    private void applySpeedFromField() {
        Game g = gameService.getGame();
        if (g == null) return;
        String txt = speedField.getText();
        if (txt == null || txt.isBlank()) {
            showWarn("Please enter a positive number for the game speed (e.g., 1.0, 2.0, 0.5).");
            return;
        }
        try {
            double v = Double.parseDouble(txt);
            if (v <= 0) throw new IllegalArgumentException("Speed must be > 0");
            g.getGameTime().setGameSpeed(v);
            speedLabel.setText(speedText(v));
            // Clear after apply so the gray placeholder "1.0" returns
            speedField.clear();
        } catch (Exception ex) {
            showWarn("Invalid speed. Use a positive number (e.g., 1.0, 2.0, 0.5).");
            // keep user's input for correction, or uncomment next line to clear:
            // speedField.clear();
        }
    }

    private void refreshPrestigeTable() {
        Game g = gameService.getGame();
        if (g == null) return;

        List<FamilyPrestigeRow> rows = new ArrayList<>();
        if (g.getFamilies() != null) {
            for (Family f : g.getFamilies()) {
                double sum = 0.0;
                if (f.getTeams() != null) {
                    for (Team t : f.getTeams()) {
                        sum += (t != null ? t.getPrestige() : 0.0);
                    }
                }
                rows.add(new FamilyPrestigeRow(f.getName(), sum));
            }
        }

        if (prestigeTable.getItems() == null) {
            prestigeTable.getItems().addAll(rows);
        } else {
            prestigeTable.getItems().setAll(rows);
        }
    }

    private static String speedText(double v) {
        return String.format("(%.2fx)", v);
    }

    private static String formatSecondsFloor(double scaledSeconds) {
        long s = (long) Math.floor(scaledSeconds);
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static void showWarn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Warning");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // simple row type for the table
    public static class FamilyPrestigeRow {
        private final String familyName;
        private final double prestigeSum;

        public FamilyPrestigeRow(String familyName, double prestigeSum) {
            this.familyName = familyName;
            this.prestigeSum = prestigeSum;
        }
        public String getFamilyName() { return familyName; }
        public double getPrestigeSum() { return prestigeSum; }
    }
}

package com.example.controller;

import com.example.model.Game;
import com.example.model.GameTime;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class GameController {

    private final GameService gameService;
    private final SceneManager sceneManager;

    private Timeline uiTicker;

    public GameController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    @FXML private Label gameNameLabel;
    @FXML private Label timeLabel;
    @FXML private Label speedLabel;

    @FXML private Button pauseBtn;
    @FXML private Button resumeBtn;
    @FXML private Button stopBtn;

    @FXML private Button slowerBtn;
    @FXML private Button normalBtn;
    @FXML private Button fasterBtn;

    @FXML
    private void initialize() {
        Game g = gameService.getGame();
        if (g != null) {
            gameNameLabel.setText("Playing: " + g.getName());
            updateTimeLabel(g.getGameTime());
            updateSpeedLabel(g.getGameTime());
        }

        // Buttons
        pauseBtn.setOnAction(e -> gameService.pauseGame());
        resumeBtn.setOnAction(e -> gameService.resumeGame());
        stopBtn.setOnAction(e -> {
            gameService.stopGame();
            sceneManager.showLauncher();
            if (uiTicker != null) uiTicker.stop();
        });

        slowerBtn.setOnAction(e -> changeSpeed(0.5));
        normalBtn.setOnAction(e -> setSpeed(1.0));
        fasterBtn.setOnAction(e -> changeSpeed(2.0));

        // UI-Update jede Sekunde
        if (uiTicker != null) uiTicker.stop();
        uiTicker = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            Game game = gameService.getGame();
            if (game != null) {
                updateTimeLabel(game.getGameTime());
            }
        }));
        uiTicker.setCycleCount(Timeline.INDEFINITE);
        uiTicker.play();
    }

    private void changeSpeed(double factor) {
        Game g = gameService.getGame();
        if (g == null) return;
        GameTime gt = g.getGameTime();
        double newSpeed = Math.max(0.1, gt.getGameSpeed() * factor);
        gt.setGameSpeed(newSpeed);
        updateSpeedLabel(gt);
    }

    private void setSpeed(double speed) {
        Game g = gameService.getGame();
        if (g == null) return;
        g.getGameTime().setGameSpeed(speed);
        updateSpeedLabel(g.getGameTime());
    }

    private void updateTimeLabel(GameTime gt) {
        long s = (long) Math.floor(gt.getScaledSeconds());
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        timeLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void updateSpeedLabel(GameTime gt) {
        speedLabel.setText(String.format("(%.2fx)", gt.getGameSpeed()));
    }
}

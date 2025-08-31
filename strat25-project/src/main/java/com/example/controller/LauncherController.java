package com.example.controller;

import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class LauncherController {

    private final GameService gameService;
    private final SceneManager sceneManager;

    public LauncherController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    @FXML private TextField saveNameField;
    @FXML private Button newBtn;
    @FXML private Button loadBtn;
    @FXML private Button loadBackupBtn;
    @FXML private Button refreshSavesBtn;
    @FXML private Button refreshBackupsBtn;
    @FXML private ComboBox<String> savesBox;
    @FXML private ComboBox<String> backupsBox;

    @FXML
    private void initialize() {
        // Buttons
        newBtn.setOnAction(e -> onNewGame());
        loadBtn.setOnAction(e -> onLoadSelectedSave());
        loadBackupBtn.setOnAction(e -> onLoadSelectedBackup());
        refreshSavesBtn.setOnAction(e -> refreshSaves());
        refreshBackupsBtn.setOnAction(e -> refreshBackups());

        // Wenn Save gewählt wird, Namen ins Textfeld übernehmen & Backups aktualisieren
        savesBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                saveNameField.setText(newV);
                refreshBackups();
            } else {
                backupsBox.setItems(FXCollections.observableArrayList());
            }
        });

        // initial laden
        refreshSaves();
    }

    private void onNewGame() {
        String name = readName();
        if (name.isEmpty()) { warn("Please enter a name for the new game."); return; }
        try {
            gameService.buildNewGame(name);
            gameService.startGame();
            sceneManager.showGame();
        } catch (Exception ex) {
            error("Failed to create new game:\n" + ex.getMessage());
        }
    }

    private void onLoadSelectedSave() {
        String selected = savesBox.getValue();
        String name = (selected != null && !selected.isBlank()) ? selected : readName();
        if (name.isEmpty()) { warn("Pick a save from the list or type its name."); return; }
        try {
            gameService.loadGame(name);
            gameService.startGame();
            sceneManager.showGame();
        } catch (Exception ex) {
            error("Failed to load game \"" + name + "\":\n" + ex.getMessage());
        }
    }

    private void onLoadSelectedBackup() {
        String save = savesBox.getValue();
        String backup = backupsBox.getValue();
        if (save == null || save.isBlank()) { warn("Select a base save first."); return; }
        if (backup == null || backup.isBlank()) { warn("Select a backup to load."); return; }

        // Hier rufst du deine Backup-Load-Methode auf (implementiere sie im GameService/Repository)
        try {
            // Beispiel-API: gameService.loadBackup(save, backup);
            // Placeholder:
            warn("Implement GameService.loadBackup(saveName, backupFile).");
            // Wenn vorhanden:
            // gameService.loadBackup(save, backup);
            // gameService.startGame();
            // sceneManager.showGame();
        } catch (Exception ex) {
            error("Failed to load backup:\n" + ex.getMessage());
        }
    }

    private void refreshSaves() {
        List<String> names = List.of(); // fallback
        try { names = gameService.listSaves(); } catch (Exception ignored) {}
        savesBox.setItems(FXCollections.observableArrayList(names));
        savesBox.getSelectionModel().clearSelection();
        backupsBox.setItems(FXCollections.observableArrayList()); // leeren, bis ein Save gewählt wurde
    }

    private void refreshBackups() {
        String save = savesBox.getValue();
        if (save == null || save.isBlank()) {
            backupsBox.setItems(FXCollections.observableArrayList());
            return;
        }
        List<String> backups = List.of(); // fallback
        try { backups = gameService.listBackups(save); } catch (Exception ignored) {}
        backupsBox.setItems(FXCollections.observableArrayList(backups));
        backupsBox.getSelectionModel().clearSelection();
    }

    private String readName() {
        String s = saveNameField.getText();
        return s == null ? "" : s.trim();
    }

    // Alerts
    private static void warn(String msg) { show(Alert.AlertType.WARNING, "Warning", msg); }
    private static void error(String msg){ show(Alert.AlertType.ERROR, "Error", msg); }
    private static void show(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}

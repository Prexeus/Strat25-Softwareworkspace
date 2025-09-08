package com.example.controller;

import com.example.service.GameService;
import com.example.service.NodeMode;
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

    @FXML private ToggleGroup modeGroup;        // via FXML <ToggleGroup fx:id="modeGroup"/>
    @FXML private RadioButton hostModeBtn;
    @FXML private RadioButton slaveModeBtn;
    @FXML private TextField hostAddressField;   // Ziel-IP des Hosts (bei Slave)

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
        // --- Sicherstellen, dass eine ToggleGroup existiert & Buttons drin sind ---
        if (modeGroup == null) {
            modeGroup = new ToggleGroup();
        }
        if (hostModeBtn != null && hostModeBtn.getToggleGroup() == null) {
            hostModeBtn.setToggleGroup(modeGroup);
        }
        if (slaveModeBtn != null && slaveModeBtn.getToggleGroup() == null) {
            slaveModeBtn.setToggleGroup(modeGroup);
        }
        // Falls noch nichts selektiert: Host als Default
        if (modeGroup.getSelectedToggle() == null && hostModeBtn != null) {
            hostModeBtn.setSelected(true);
        }

        // Listener: UI -> Mode anwenden
        modeGroup.selectedToggleProperty().addListener((obs, o, n) ->
                applyMode(slaveModeBtn != null && slaveModeBtn.isSelected()));

        // UI initial an aktuellen Zustand anpassen
        applyMode(slaveModeBtn != null && slaveModeBtn.isSelected());

        // Buttons
        newBtn.setOnAction(e -> onNewGame());
        loadBtn.setOnAction(e -> onLoadSelectedSave());
        loadBackupBtn.setOnAction(e -> onLoadSelectedBackup());
        refreshSavesBtn.setOnAction(e -> refreshSaves());
        refreshBackupsBtn.setOnAction(e -> refreshBackups());

        // Wenn Save gewählt wird, Namen ins Textfeld übernehmen & Backups aktualisieren
        if (savesBox != null) {
            savesBox.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    if (saveNameField != null) saveNameField.setText(newV);
                    refreshBackups();
                } else {
                    if (backupsBox != null)
                        backupsBox.setItems(FXCollections.observableArrayList());
                }
            });
        }

        // initial laden
        refreshSaves();
    }

    /** Zentral: Mode setzen + UI deaktivieren/aktivieren */
    private void applyMode(boolean slave) {
        if (slave) {
            String addr = (hostAddressField != null) ? hostAddressField.getText() : null;
            gameService.setHostAddress(addr);
            gameService.setNodeMode(NodeMode.SLAVE);
        } else {
            gameService.setNodeMode(NodeMode.HOST);
        }

        // UI anpassen
        if (saveNameField != null) saveNameField.setDisable(slave);
        if (newBtn != null) newBtn.setDisable(slave);
        if (savesBox != null) savesBox.setDisable(slave);
        if (refreshSavesBtn != null) refreshSavesBtn.setDisable(slave);
        if (loadBtn != null) loadBtn.setDisable(slave);
        if (backupsBox != null) backupsBox.setDisable(slave);
        if (refreshBackupsBtn != null) refreshBackupsBtn.setDisable(slave);
        if (loadBackupBtn != null) loadBackupBtn.setDisable(slave);
        if (hostAddressField != null) hostAddressField.setDisable(!slave);
    }

    private boolean isSlave() {
        return gameService.getNodeMode() == NodeMode.SLAVE;
    }

    private void onNewGame() {
        if (isSlave()) {
            // Kein Save nötig – gleich ins Spiel (gleiches Fenster)
            sceneManager.showGame();
            return;
        }
        String name = readName();
        if (name.isEmpty()) { warn("Please enter a name for the new game."); return; }
        try {
            gameService.buildNewGame(name);
            sceneManager.showGame();
        } catch (Exception ex) {
            error("Failed to create new game:\n" + ex.getMessage());
        }
    }

    private void onLoadSelectedSave() {
        if (isSlave()) {
            sceneManager.showGame();
            return;
        }
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
        if (isSlave()) {
            sceneManager.showGame();
            return;
        }
        String save = savesBox.getValue();
        String backup = backupsBox.getValue();
        if (save == null || save.isBlank()) { warn("Select a base save first."); return; }
        if (backup == null || backup.isBlank()) { warn("Select a backup to load."); return; }
        try {
            warn("Implement GameService.loadBackup(saveName, backupFile).");
        } catch (Exception ex) {
            error("Failed to load backup:\n" + ex.getMessage());
        }
    }

    private void refreshSaves() {
        if (isSlave()) {
            if (savesBox != null) savesBox.setItems(FXCollections.observableArrayList());
            if (backupsBox != null) backupsBox.setItems(FXCollections.observableArrayList());
            return;
        }
        List<String> names = List.of(); // fallback
        try { names = gameService.listSaves(); } catch (Exception ignored) {}
        if (savesBox != null) {
            savesBox.setItems(FXCollections.observableArrayList(names));
            savesBox.getSelectionModel().clearSelection();
        }
        if (backupsBox != null) backupsBox.setItems(FXCollections.observableArrayList());
    }

    private void refreshBackups() {
        if (isSlave()) {
            if (backupsBox != null) backupsBox.setItems(FXCollections.observableArrayList());
            return;
        }
        String save = (savesBox != null) ? savesBox.getValue() : null;
        if (save == null || save.isBlank()) {
            if (backupsBox != null) backupsBox.setItems(FXCollections.observableArrayList());
            return;
        }
        List<String> backups = List.of(); // fallback
        try { backups = gameService.listBackups(save); } catch (Exception ignored) {}
        if (backupsBox != null) {
            backupsBox.setItems(FXCollections.observableArrayList(backups));
            backupsBox.getSelectionModel().clearSelection();
        }
    }

    private String readName() {
        String s = (saveNameField != null) ? saveNameField.getText() : "";
        return s == null ? "" : s.trim();
    }

    // Alerts
    private static void warn(String msg) { show(Alert.AlertType.WARNING, "Warning", msg); }
    private static void error(String msg){ show(Alert.AlertType.ERROR, "Error", msg); }
    private static void show(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}

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

    @FXML private ToggleGroup modeGroup;        // via FXML <fx:define>
    @FXML private RadioButton hostModeBtn;
    @FXML private RadioButton slaveModeBtn;
    @FXML private TextField hostAddressField;

    @FXML private Button openSlaveBtn;          // „Als Slave öffnen“
    @FXML private Button testConnBtn;           // „Verbindung testen“

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
        // ToggleGroup robust sicherstellen
        if (modeGroup == null) modeGroup = new ToggleGroup();
        if (hostModeBtn != null && hostModeBtn.getToggleGroup() == null) hostModeBtn.setToggleGroup(modeGroup);
        if (slaveModeBtn != null && slaveModeBtn.getToggleGroup() == null) slaveModeBtn.setToggleGroup(modeGroup);
        if (modeGroup.getSelectedToggle() == null && hostModeBtn != null) hostModeBtn.setSelected(true);

        // Live auf Mode-Wechsel reagieren
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> applyMode(isSlaveSelected()));

        // Live auf Adressänderungen reagieren (aktiviert/deaktiviert die Slave-Knöpfe)
        if (hostAddressField != null) {
            hostAddressField.textProperty().addListener((obs, ov, nv) -> applyMode(isSlaveSelected()));
        }

        // Slave-Buttons
        if (openSlaveBtn != null) {
            openSlaveBtn.setOnAction(e -> {
                gameService.setHostAddress(getHost());
                gameService.setNodeMode(NodeMode.SLAVE);
                sceneManager.showGame();
            });
        }
        if (testConnBtn != null) {
            testConnBtn.setOnAction(e -> {
                gameService.setHostAddress(getHost());
                boolean ok = gameService.testConnectionToHost();
                if (ok) info("Verbindung erfolgreich.\nHost erreichbar (Port 53536).");
                else warn("Keine Verbindung.\nPrüfe IP, Port 53536 und Firewall am Host.");
            });
        }

        // Host-Buttons
        if (newBtn != null) newBtn.setOnAction(e -> onNewGame());
        if (loadBtn != null) loadBtn.setOnAction(e -> onLoadSelectedSave());
        if (loadBackupBtn != null) loadBackupBtn.setOnAction(e -> onLoadSelectedBackup());
        if (refreshSavesBtn != null) refreshSavesBtn.setOnAction(e -> refreshSaves());
        if (refreshBackupsBtn != null) refreshBackupsBtn.setOnAction(e -> refreshBackups());

        if (savesBox != null) {
            savesBox.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    if (saveNameField != null) saveNameField.setText(newV);
                    refreshBackups();
                } else if (backupsBox != null) {
                    backupsBox.setItems(FXCollections.observableArrayList());
                }
            });
        }

        // UI initial konsistent setzen
        applyMode(isSlaveSelected());
        refreshSaves(); // lädt Saves (nur wenn Host)
    }

    private boolean isSlaveSelected() {
        return slaveModeBtn != null && slaveModeBtn.isSelected();
    }

    /** Zentral: Mode setzen + UI an/aus + Slave-Buttons abhängig von Host-Adresse. */
    private void applyMode(boolean slave) {
        if (slave) {
            gameService.setHostAddress(getHost());
            gameService.setNodeMode(NodeMode.SLAVE);
        } else {
            gameService.setNodeMode(NodeMode.HOST);
        }

        // Host-UI
        setDisable(saveNameField, slave);
        setDisable(newBtn, slave);
        setDisable(savesBox, slave);
        setDisable(refreshSavesBtn, slave);
        setDisable(loadBtn, slave);
        setDisable(backupsBox, slave);
        setDisable(refreshBackupsBtn, slave);
        setDisable(loadBackupBtn, slave);

        // Slave-UI
        setDisable(hostAddressField, !slave);

        boolean hasAnyAddress = !getHost().isBlank();
        boolean validAddress  = isValidHost(getHost());

        // „Verbindung testen“: nur Slave + irgendein Text
        setDisable(testConnBtn, !slave || !hasAnyAddress);
        // „Als Slave öffnen“: nur Slave + plausible Adresse (IPv4/Hostname)
        setDisable(openSlaveBtn, !slave || !validAddress);
    }

    private void setDisable(Control c, boolean disable) {
        if (c != null) c.setDisable(disable);
    }

    private String getHost() {
        String s = hostAddressField != null ? hostAddressField.getText() : "";
        return s == null ? "" : s.trim();
    }

    // sehr einfache Plausibilitätsprüfung (IPv4 oder Hostname)
    private boolean isValidHost(String s) {
        if (s == null || s.isBlank()) return false;
        // IPv4
        if (s.matches("((25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.|$)){4}")) return true;
        // Hostnamen (locker)
        return s.matches("^[A-Za-z0-9._-]{1,253}$");
    }

    private boolean isSlave() { return gameService.getNodeMode() == NodeMode.SLAVE; }

    private void onNewGame() {
        if (isSlave()) { sceneManager.showGame(); return; }
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
        if (isSlave()) { sceneManager.showGame(); return; }
        String selected = savesBox != null ? savesBox.getValue() : null;
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
        if (isSlave()) { sceneManager.showGame(); return; }
        String save = savesBox != null ? savesBox.getValue() : null;
        String backup = backupsBox != null ? backupsBox.getValue() : null;
        if (save == null || save.isBlank()) { warn("Select a base save first."); return; }
        if (backup == null || backup.isBlank()) { warn("Select a backup to load."); return; }
        warn("Implement GameService.loadBackup(saveName, backupFile).");
    }

    private void refreshSaves() {
        if (isSlave()) {
            if (savesBox != null) savesBox.setItems(FXCollections.observableArrayList());
            if (backupsBox != null) backupsBox.setItems(FXCollections.observableArrayList());
            return;
        }
        List<String> names = List.of();
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
        List<String> backups = List.of();
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
    private static void info(String msg) { show(Alert.AlertType.INFORMATION, "Info", msg); }
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

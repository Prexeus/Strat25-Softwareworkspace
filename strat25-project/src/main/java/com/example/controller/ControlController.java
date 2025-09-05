package com.example.controller;

import com.example.model.BuildCategory;
import com.example.model.CategoryInterface;
import com.example.model.Game;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.*;

public class ControlController {

    private final GameService gameService;
    private final SceneManager sceneManager;

    public ControlController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    // UI aus FXML
    @FXML
    private VBox categoryBox;
    @FXML
    private CheckBox endscoreCheckBox;
    @FXML
    private Label statusLabel;

    // Alle Kategorienamen -> Checkbox
    private final Map<String, CheckBox> categoryChecks = new LinkedHashMap<>();
    // Nur Build-Kategorien -> Checkbox (für Entweder-Oder)
    private final Map<String, CheckBox> buildChecks = new LinkedHashMap<>();

    @FXML
    private void initialize() {
        Game g = gameService.getGame();
        if (g != null && g.getCategories() != null) {
            List<CategoryInterface> cats = g.getCategories();

            // 1) Checkboxen anlegen
            for (CategoryInterface ci : cats) {
                String name = ci.getName();
                if (name == null || name.isBlank())
                    continue;

                CheckBox cb = new CheckBox(name);

                // BuildCategory -> mutual exclusive
                boolean isBuild = ci instanceof BuildCategory;
                if (isBuild)
                    buildChecks.put(name, cb);

                // Default-States:
                // - Nicht-Build: sichtbar (true)
                // - Build: wir setzen gleich GENAU EINE auf true
                cb.setSelected(!isBuild);

                categoryChecks.put(name, cb);
                categoryBox.getChildren().add(cb);
            }

            // 2) Genau EINE Build-Checkbox aktiv lassen (Standard: die erste)
            if (!buildChecks.isEmpty()) {
                boolean first = true;
                for (CheckBox bcb : buildChecks.values()) {
                    bcb.setSelected(first);
                    first = false;
                }

                // 3) Mutual-Exclusion-Handler
                for (CheckBox bcb : buildChecks.values()) {
                    bcb.setOnAction(ev -> {
                        if (bcb.isSelected()) {
                            // alle anderen Build-Checkboxen deaktivieren
                            for (CheckBox other : buildChecks.values()) {
                                if (other != bcb)
                                    other.setSelected(false);
                            }
                        } else {
                            // mindestens eine muss aktiv bleiben
                            boolean any = buildChecks.values().stream().anyMatch(CheckBox::isSelected);
                            if (!any)
                                bcb.setSelected(true);
                        }
                    });
                }
            }
        }

        setStatus("Kategorien geladen: " + categoryChecks.size());
    }

    @FXML
    private void onOpenResult() {
        sceneManager.showResultWindow();
        onApplyVisibility(); // aktuelle Auswahl direkt pushen
    }

    @FXML
    private void onToggleFullscreen() {
        sceneManager.toggleResultFullscreen();
    }

    @FXML
    private void onHideResult() {
        sceneManager.hideResultWindow();
    }

    @FXML
    private void onApplyVisibility() {
        Set<String> selected = new LinkedHashSet<>();
        for (Map.Entry<String, CheckBox> e : categoryChecks.entrySet()) {
            if (e.getValue().isSelected())
                selected.add(e.getKey());
        }
        boolean showEndscore = endscoreCheckBox != null && endscoreCheckBox.isSelected();

        sceneManager.updateResultVisibility(selected, showEndscore);
        setStatus("Übernommen (" + selected.size() + " Kategorien"
                + (showEndscore ? ", Endscores" : "") + ")");
    }

    private void setStatus(String txt) {
        if (statusLabel == null)
            return;
        Platform.runLater(() -> statusLabel.setText(txt));
    }

    @FXML
    private void onMinimizeResult() {
        sceneManager.minimizeResultWindow();
    }

    @FXML
    private void onMinimizeControl() {
        sceneManager.minimizeControlWindow();
    }

}

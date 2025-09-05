package com.example.controller;

import com.example.model.BuildCategory;
import com.example.model.Game;
import com.example.model.Material;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ResultController {

    private final GameService gameService;
    @SuppressWarnings("unused")
    private final SceneManager sceneManager;

    public ResultController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    // FXML
    @FXML private Label activeBuildLabel;
    @FXML private Label phaseTitleLabel;
    @FXML private StackPane imageHolder;
    @FXML private ImageView phaseImageView;
    @FXML private ListView<String> materialsList;
    @FXML private VBox rightBottomBox; // Endscore-Bereich

    // Zustand
    private BuildCategory activeBuild;        // aktuell ausgewählte Baukategorie (oder null)
    private boolean showEndscore;             // vom Control-Fenster gesetzt

    // Caches zur Flacker-/Wobble-Vermeidung
    private String lastBuildName   = null;
    private int    lastPhase       = -1;
    private String lastPhaseTitle  = null;
    private String lastImageUrl    = null;
    private List<String> lastMaterialsLines = List.of();

    private Timeline ticker;

    @FXML
    private void initialize() {
        setupImageSizing();
        setupListViewSizing();

        if (ticker != null) ticker.stop();
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> safeRefresh()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    /** Bild sauber an den begrenzten Container binden. */
    private void setupImageSizing() {
        if (imageHolder == null || phaseImageView == null) return;

        phaseImageView.setPreserveRatio(true);
        phaseImageView.setSmooth(true);
        phaseImageView.setCache(true);

        // An Containerbreite/-höhe binden – die StackPane ist im FXML in der Höhe gedeckelt
        phaseImageView.fitWidthProperty().bind(imageHolder.widthProperty());
        phaseImageView.fitHeightProperty().bind(imageHolder.heightProperty());
    }

    /** ListView so konfigurieren, dass sie immer alle Items zeigt (ohne Scrollbar). */
    private void setupListViewSizing() {
        if (materialsList == null) return;

        // Feste Zellhöhe; damit können wir die Gesamt-Höhe exakt berechnen.
        materialsList.setFixedCellSize(24);

        // ListView lässt sich von VBox nicht „strecken“, sondern behält ihre Pref-Höhe.
        materialsList.setMinHeight(Region.USE_PREF_SIZE);
        materialsList.setMaxHeight(Region.USE_PREF_SIZE);
    }

    /** Wird vom SceneManager/ControlController aufgerufen. */
    public void applyVisibility(Set<String> visibleCategoryNames, boolean showEndscore) {
        this.showEndscore = showEndscore;

        // genau eine Baukategorie aus den sichtbaren Namen wählen (falls vorhanden)
        BuildCategory newActive = pickBuildCategoryByNames(visibleCategoryNames);

        // nur dann umschalten, wenn sich die aktive Kategorie wirklich ändert
        if (activeBuild != newActive) {
            activeBuild = newActive;
            // Caches invalidieren → nächste Refresh-Runde rendert hart neu
            lastBuildName = null;
            lastPhase = -1;
            lastPhaseTitle = null;
            lastImageUrl = null;
            lastMaterialsLines = List.of();
        }

        // Endscore-Box sichtbar?
        if (rightBottomBox != null) {
            rightBottomBox.setVisible(this.showEndscore);
            rightBottomBox.setManaged(this.showEndscore);
        }

        safeRefresh(); // sofort einmal anwenden
    }

    private void safeRefresh() {
        try {
            refreshIfChanged();
        } catch (Exception ignored) {
            // robust gegen sporadische NPEs während Szenenwechseln
        }
    }

    /** Aktualisiert UI nur, wenn sich etwas geändert hat. */
    private void refreshIfChanged() {
        final BuildCategory bc = activeBuild;

        // 1) aktives Bauspiel & Etappentitel
        String buildName   = (bc != null) ? nullToDash(bc.getName()) : "—";
        int    phase       = (bc != null) ? bc.getConstructionPhase() : -1;
        String phaseTitle  = (bc != null) ? nullToDash(bc.getCurrentPhaseTitle()) : "—";

        if (!Objects.equals(buildName, lastBuildName)) {
            if (activeBuildLabel != null) activeBuildLabel.setText(buildName);
            lastBuildName = buildName;
        }
        if (phase != lastPhase || !Objects.equals(phaseTitle, lastPhaseTitle)) {
            if (phaseTitleLabel != null) phaseTitleLabel.setText(phaseTitle);
            lastPhase = phase;
            lastPhaseTitle = phaseTitle;
        }

        // 2) Bild (nur bei Änderung setzen → kein Flackern)
        String imgUrl = null;
        if (bc != null) {
            Optional<URL> urlOpt = bc.getCurrentPhaseImageUrl();
            if (urlOpt.isPresent()) {
                imgUrl = urlOpt.get().toExternalForm();
            }
        }
        if (!Objects.equals(imgUrl, lastImageUrl)) {
            if (phaseImageView != null) {
                if (imgUrl == null) {
                    phaseImageView.setImage(null);
                } else {
                    // Original laden, Skalierung übernimmt die ImageView
                    Image img = new Image(imgUrl, 0, 0, true, true, true);
                    phaseImageView.setImage(img);
                }
            }
            lastImageUrl = imgUrl;
        }

        // 3) Ressourcenliste (z. B. "4/8 HOLZ")
        List<String> lines = (bc != null) ? buildMaterialsLines(bc) : List.of();
        if (!lines.equals(lastMaterialsLines)) {
            if (materialsList != null) {
                materialsList.getItems().setAll(lines);
                resizeMaterialsListToFitContent();
            }
            lastMaterialsLines = lines;
        }
    }

    /** Setzt die ListView-Höhe so, dass alle Zeilen sichtbar sind (keine Scrollbar). */
    private void resizeMaterialsListToFitContent() {
        if (materialsList == null) return;

        double cell = materialsList.getFixedCellSize() > 0
                ? materialsList.getFixedCellSize()
                : 24;

        int rows = (materialsList.getItems() != null) ? materialsList.getItems().size() : 0;
        double newHeight = rows * cell + 2; // +2 px für Borders/Insets

        materialsList.setPrefHeight(newHeight);
        materialsList.setMinHeight(newHeight);
        materialsList.setMaxHeight(newHeight);
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private List<String> buildMaterialsLines(BuildCategory bc) {
        Map<Material, Integer> need = bc.getNeededMaterials();
        Map<Material, Integer> pay  = bc.getPayedMaterials();

        return Arrays.stream(Material.values())
                .map(m -> {
                    int n = need.getOrDefault(m, 0);
                    int p = pay.getOrDefault(m, 0);
                    if (n == 0 && p == 0) return null;
                    return String.format("%d/%d %s", p, n, m.name());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BuildCategory pickBuildCategoryByNames(Set<String> names) {
        if (names == null || names.isEmpty()) return null;
        Game g = gameService.getGame();
        if (g == null || g.getCategories() == null) return null;

        // Kandidaten: nur BuildCategory, deren Name in names enthalten ist
        List<BuildCategory> candidates = g.getCategories().stream()
                .filter(c -> c instanceof BuildCategory)
                .map(c -> (BuildCategory) c)
                .filter(bc -> names.contains(bc.getName()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // Falls mehrere angehakt wurden, nimm die erste (Control-Fenster enforced später Single-Choice)
        return candidates.get(0);
    }

    /** Aufräumen (optional vom SceneManager beim Schließen des Fensters aufrufen). */
    public void shutdown() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
    }
}

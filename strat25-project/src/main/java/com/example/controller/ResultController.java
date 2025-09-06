package com.example.controller;

import com.example.model.BuildCategory;
import com.example.model.CategoryInterface;
import com.example.model.Family;
import com.example.model.Game;
import com.example.model.Material;
import com.example.model.Team;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.lang.reflect.Method;
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

    // ===== FXML (Hauptbereich rechts) =====
    @FXML
    private Label activeBuildLabel;
    @FXML
    private Label phaseTitleLabel;

    @FXML
    private StackPane imageHolder;
    @FXML
    private ImageView phaseImageView;

    @FXML
    private StackPane pieHolder;
    @FXML
    private PieChart influencePieChart;
    @FXML
    private ImageView pieCenterImage;

    @FXML
    private ListView<String> materialsList;
    @FXML
    private VBox rightBottomBox; // Endscore-Bereich

    // ===== FXML (linke Seite: extra Charts) =====
    @FXML
    private GridPane extraChartsGrid;

    @FXML
    private StackedBarChart<Number, String> prestigeChart;
    @FXML
    private NumberAxis prestigeXAxis;
    @FXML
    private CategoryAxis prestigeYAxis;

    // ===== Zustand =====
    private BuildCategory activeBuild;
    private boolean showEndscore;

    // Caches: Hauptbereich
    private String lastBuildName = null;
    private int lastPhase = -1;
    private String lastPhaseTitle = null;
    private String lastImageUrl = null;
    private String lastCenterImgUrl = null;
    private List<String> lastMaterialsLines = List.of();
    private String lastPieSignature = "";
    private String lastPrestigeSignature = "";

    // Extra-Charts Verwaltung + Caches
    private List<String> lastExtraNames = List.of();
    private final Map<String, PieChart> extraChartsByName = new LinkedHashMap<>();
    private final Map<String, ImageView> extraIconsByName = new LinkedHashMap<>();
    private final Map<String, Label> extraTitlesByName = new LinkedHashMap<>();
    private final Map<String, String> extraPieSignatureByName = new HashMap<>();
    private final Map<String, String> extraIconUrlByName = new HashMap<>();

    private Label pieEmptyOverlay;
    private Timeline ticker;

    // ===== Lifecycle =====
    @FXML
    private void initialize() {
        setupImageSizing();
        setupListViewSizing();
        setupMainPieChartSizingAndStyle();
        setupPieEmptyOverlay();

        if (ticker != null)
            ticker.stop();
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> safeRefresh()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    // ===== Setup: Bild =====
    private void setupImageSizing() {
        if (imageHolder == null || phaseImageView == null)
            return;
        phaseImageView.setPreserveRatio(true);
        phaseImageView.setSmooth(true);
        phaseImageView.setCache(true);
        phaseImageView.fitWidthProperty().bind(imageHolder.widthProperty());
        phaseImageView.fitHeightProperty().bind(imageHolder.heightProperty());
    }

    // ===== Setup: Liste (Materialien) =====
    private void setupListViewSizing() {
        if (materialsList == null)
            return;
        materialsList.setFixedCellSize(24);
        materialsList.setMinHeight(Region.USE_PREF_SIZE);
        materialsList.setMaxHeight(Region.USE_PREF_SIZE);
    }

    // ===== Setup: Haupt-PieChart + größeres Center-Icon =====
    private void setupMainPieChartSizingAndStyle() {
        if (influencePieChart != null) {
            influencePieChart.setLegendVisible(false);
            influencePieChart.setLabelsVisible(false);
            influencePieChart.setAnimated(false);
            influencePieChart.prefWidthProperty().bind(pieHolder.widthProperty());
            influencePieChart.prefHeightProperty().bind(pieHolder.heightProperty());
        }
        if (pieCenterImage != null && pieHolder != null) {
            pieCenterImage.setPreserveRatio(true);
            pieCenterImage.setSmooth(true);
            pieCenterImage.setMouseTransparent(true);
            // 65% -> 70%
            var centerSize = Bindings.createDoubleBinding(
                    () -> 0.70 * Math.min(pieHolder.getWidth(), pieHolder.getHeight()),
                    pieHolder.widthProperty(), pieHolder.heightProperty());
            pieCenterImage.fitWidthProperty().bind(centerSize);
            pieCenterImage.fitHeightProperty().bind(centerSize);
        }
    }

    private void setupPieEmptyOverlay() {
        if (pieHolder == null)
            return;
        pieEmptyOverlay = new Label("Keine Einflussdaten");
        pieEmptyOverlay.setMouseTransparent(true);
        pieEmptyOverlay.setStyle("-fx-font-size: 16; -fx-opacity: 0.7; -fx-text-fill: -fx-text-base-color;");
        pieHolder.getChildren().add(pieEmptyOverlay);
        pieEmptyOverlay.setVisible(false);
    }

    // ===== API vom Control/SceneManager =====
    public void applyVisibility(Set<String> visibleCategoryNames, boolean showEndscore) {
        this.showEndscore = showEndscore;

        BuildCategory newActive = pickBuildCategoryByNames(visibleCategoryNames);

        if (activeBuild != newActive) {
            activeBuild = newActive;
            // Caches invalidieren
            lastBuildName = null;
            lastPhase = -1;
            lastPhaseTitle = null;
            lastImageUrl = null;
            lastCenterImgUrl = null;
            lastMaterialsLines = List.of();
            lastPieSignature = "";
        }

        if (rightBottomBox != null) {
            rightBottomBox.setVisible(this.showEndscore);
            rightBottomBox.setManaged(this.showEndscore);
        }

        // Linke Seite: erste 4 Nicht-Baukategorien, die im Control angehakt sind
        List<CategoryInterface> extras = firstFourNonBuildCategories(visibleCategoryNames);
        rebuildExtraChartsGridIfNeeded(extras);

        safeRefresh();
    }

    // ===== Refresh =====
    private void safeRefresh() {
        try {
            refreshIfChanged();
        } catch (Exception ignored) {
        }
    }

    private void refreshIfChanged() {
        final BuildCategory bc = activeBuild;

        // 1) Überschriften (DisplayName bevorzugt)
        String displayName = "—";
        if (bc != null) {
            try {
                String dn = bc.getDisplayName();
                displayName = (dn != null && !dn.isBlank()) ? dn : bc.getName();
            } catch (Throwable ex) {
                displayName = (bc.getName() != null && !bc.getName().isBlank()) ? bc.getName() : "—";
            }
        }
        int phase = (bc != null) ? bc.getConstructionPhase() : -1;
        String phaseTitle = (bc != null) ? nullToDash(bc.getCurrentPhaseTitle()) : "—";

        if (!Objects.equals(displayName, lastBuildName)) {
            if (activeBuildLabel != null)
                activeBuildLabel.setText(displayName);
            lastBuildName = displayName;
        }
        if (phase != lastPhase || !Objects.equals(phaseTitle, lastPhaseTitle)) {
            if (phaseTitleLabel != null)
                phaseTitleLabel.setText(phaseTitle);
            lastPhase = phase;
            lastPhaseTitle = phaseTitle;
        }

        // 2) Phasenbild
        String imgUrl = null;
        if (bc != null) {
            Optional<URL> urlOpt = bc.getCurrentPhaseImageUrl();
            if (urlOpt.isPresent())
                imgUrl = urlOpt.get().toExternalForm();
        }
        if (!Objects.equals(imgUrl, lastImageUrl)) {
            if (phaseImageView != null) {
                phaseImageView.setImage(imgUrl == null ? null : new Image(imgUrl, 0, 0, true, true, true));
            }
            lastImageUrl = imgUrl;
        }

        // 3) Haupt-Pie Center-Icon
        String centerUrl = null;
        if (bc != null) {
            Optional<URL> urlOpt = bc.getImageUrl();
            if (urlOpt.isPresent())
                centerUrl = urlOpt.get().toExternalForm();
        }
        if (!Objects.equals(centerUrl, lastCenterImgUrl)) {
            if (pieCenterImage != null) {
                pieCenterImage.setImage(centerUrl == null ? null : new Image(centerUrl, true));
            }
            lastCenterImgUrl = centerUrl;
        }

        // 4) Ressourcenliste
        List<String> lines = (bc != null) ? buildMaterialsLines(bc) : List.of();
        if (!lines.equals(lastMaterialsLines)) {
            if (materialsList != null) {
                materialsList.getItems().setAll(lines);
                resizeMaterialsListToFitContent();
            }
            lastMaterialsLines = lines;
        }

        // 5) Haupt-Pie Daten
        Map<Integer, Double> infl = (bc != null && bc.getInfluenceMap() != null)
                ? bc.getInfluenceMap()
                : Map.of();
        rebuildMainInfluencePie(infl);

        // 6) Extra-Charts aktualisieren
        updateExtraChartsData();

        // 7) Prestige-Chart (Endscore)
        rebuildPrestigeChart();
    }

    // ===== Haupt-PieChart =====
    private void rebuildMainInfluencePie(Map<Integer, Double> influence) {
        if (influencePieChart == null)
            return;
        if (influence == null)
            influence = Map.of();

        double sum = influence.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        String signature = influence.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + String.format(Locale.ROOT, "%.4f",
                        e.getValue() == null ? 0.0 : e.getValue()))
                .collect(Collectors.joining("|"));

        if (signature.equals(lastPieSignature)) {
            if (sum <= 0)
                showPieEmptyOverlay(true);
            return;
        }
        lastPieSignature = signature;

        if (sum <= 0.0) {
            influencePieChart.getData().clear();
            showPieEmptyOverlay(true);
            return;
        }

        showPieEmptyOverlay(false);

        Map<Integer, Team> teamById = teamMapById();
        List<PieChart.Data> data = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : influence.entrySet()) {
            double val = e.getValue() != null ? e.getValue() : 0.0;
            if (val <= 0)
                continue;
            Team t = teamById.get(e.getKey());
            String label = (t != null && t.getName() != null) ? t.getName() : ("Team " + e.getKey());
            data.add(new PieChart.Data(label, val));
        }
        influencePieChart.getData().setAll(data);

        for (PieChart.Data d : data) {
            Team t = findTeamByName(teamById, d.getName());
            String cssColor = teamToCssColor(t);
            if (cssColor != null)
                applySliceColor(d, cssColor);
        }
    }

    private void showPieEmptyOverlay(boolean show) {
        if (pieEmptyOverlay != null)
            pieEmptyOverlay.setVisible(show);
    }

    // ===== Extra-PieCharts (links) =====
    private List<CategoryInterface> firstFourNonBuildCategories(Set<String> visibleNames) {
        Game g = gameService.getGame();
        if (g == null || g.getCategories() == null || visibleNames == null)
            return List.of();

        List<CategoryInterface> out = new ArrayList<>(4);
        for (CategoryInterface ci : g.getCategories()) {
            if (ci instanceof BuildCategory)
                continue; // keine Baukategorien
            String nm = ci.getName();
            if (nm == null || !visibleNames.contains(nm))
                continue; // nur angehakt
            out.add(ci);
            if (out.size() == 4)
                break;
        }
        return out;
    }

    private void rebuildExtraChartsGridIfNeeded(List<CategoryInterface> extras) {
        List<String> names = extras.stream().map(CategoryInterface::getName).toList();
        if (names.equals(lastExtraNames))
            return;

        lastExtraNames = names;

        // Caches aufräumen
        extraChartsByName.keySet().removeIf(n -> !names.contains(n));
        extraIconsByName.keySet().removeIf(n -> !names.contains(n));
        extraTitlesByName.keySet().removeIf(n -> !names.contains(n));
        extraPieSignatureByName.keySet().removeIf(n -> !names.contains(n));
        extraIconUrlByName.keySet().removeIf(n -> !names.contains(n));

        if (extraChartsGrid == null)
            return;
        extraChartsGrid.getChildren().clear();
        extraChartsByName.clear();
        extraIconsByName.clear();
        extraTitlesByName.clear();

        for (int i = 0; i < names.size(); i++) {
            String catName = names.get(i);

            // VBox: Titel + StackPane (Chart + Icon)
            VBox cell = new VBox(6);
            cell.setFillWidth(true);
            cell.setAlignment(Pos.TOP_CENTER);

            Label title = new Label(catName);
            title.setStyle("-fx-font-weight:bold; -fx-font-size:13;");
            title.setWrapText(true);
            title.setAlignment(Pos.CENTER);
            title.setMaxWidth(Double.MAX_VALUE);

            StackPane chartStack = new StackPane();
            chartStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            // NEU: nie zu klein
            chartStack.setMinHeight(160);
            VBox.setVgrow(chartStack, Priority.ALWAYS);

            PieChart chart = new PieChart();
            chart.setLegendVisible(false);
            chart.setLabelsVisible(false);
            chart.setAnimated(false);
            chart.prefWidthProperty().bind(chartStack.widthProperty());
            chart.prefHeightProperty().bind(chartStack.heightProperty());

            ImageView icon = new ImageView();
            icon.setPreserveRatio(true);
            icon.setSmooth(true);
            icon.setMouseTransparent(true);
            // 60% -> 70% (beide Achsen)
            icon.fitWidthProperty().bind(chartStack.widthProperty().multiply(0.70));
            icon.fitHeightProperty().bind(chartStack.heightProperty().multiply(0.70));

            chartStack.getChildren().addAll(chart, icon);

            cell.getChildren().addAll(title, chartStack);

            int col = i % 2;
            int row = i / 2;
            GridPane.setColumnIndex(cell, col);
            GridPane.setRowIndex(cell, row);
            GridPane.setHalignment(cell, HPos.CENTER);
            GridPane.setValignment(cell, VPos.CENTER);
            GridPane.setHgrow(cell, Priority.ALWAYS);
            GridPane.setVgrow(cell, Priority.ALWAYS);

            extraChartsGrid.getChildren().add(cell);
            extraChartsByName.put(catName, chart);
            extraIconsByName.put(catName, icon);
            extraTitlesByName.put(catName, title);
        }
    }

    private void updateExtraChartsData() {
        if (extraChartsByName.isEmpty())
            return;

        Map<Integer, Team> teamById = teamMapById();
        Game g = gameService.getGame();
        if (g == null)
            return;

        Map<String, CategoryInterface> byName = new HashMap<>();
        for (CategoryInterface ci : g.getCategories()) {
            if (ci != null && ci.getName() != null)
                byName.put(ci.getName(), ci);
        }

        for (String catName : extraChartsByName.keySet()) {
            PieChart chart = extraChartsByName.get(catName);
            ImageView icon = extraIconsByName.get(catName);
            Label title = extraTitlesByName.get(catName);

            CategoryInterface ci = byName.get(catName);
            if (title != null)
                title.setText(ci != null && ci.getName() != null ? ci.getName() : catName);

            // Icon nur bei geänderter URL setzen
            if (icon != null) {
                String url = (ci != null) ? ci.getImageUrl().map(URL::toExternalForm).orElse(null) : null;
                String lastUrl = extraIconUrlByName.get(catName);
                if (!Objects.equals(url, lastUrl)) {
                    icon.setImage(url == null ? null : new Image(url, true));
                    extraIconUrlByName.put(catName, url);
                }
            }

            if (ci == null) {
                chart.getData().clear();
                extraPieSignatureByName.remove(catName);
                continue;
            }

            Map<Integer, Double> influence = ci.getInfluenceMap();
            if (influence == null)
                influence = Map.of();

            double sum = influence.values().stream()
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            String signature = influence.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(inf -> inf.getKey() + ":" + String.format(Locale.ROOT, "%.4f",
                            inf.getValue() == null ? 0.0 : inf.getValue()))
                    .collect(Collectors.joining("|"));

            String lastSig = extraPieSignatureByName.get(catName);
            if (Objects.equals(signature, lastSig)) {
                continue; // unverändert → nichts tun
            }
            extraPieSignatureByName.put(catName, signature);

            if (sum <= 0.0) {
                chart.getData().setAll();
                continue;
            }

            List<PieChart.Data> data = new ArrayList<>();
            for (Map.Entry<Integer, Double> inf : influence.entrySet()) {
                double val = inf.getValue() != null ? inf.getValue() : 0.0;
                if (val <= 0)
                    continue;
                Team t = teamById.get(inf.getKey());
                String label = (t != null && t.getName() != null) ? t.getName() : ("Team " + inf.getKey());
                data.add(new PieChart.Data(label, val));
            }
            chart.getData().setAll(data);

            for (PieChart.Data d : data) {
                Team t = findTeamByName(teamById, d.getName());
                String cssColor = teamToCssColor(t);
                if (cssColor != null)
                    applySliceColor(d, cssColor);
            }
        }
    }

    private void applySliceColor(PieChart.Data d, String cssColor) {
        d.nodeProperty().addListener((obs, oldNode, node) -> {
            if (node != null)
                node.setStyle("-fx-pie-color: " + cssColor + ";");
        });
        Node node = d.getNode();
        if (node != null)
            node.setStyle("-fx-pie-color: " + cssColor + ";");
    }

    // ===== Hilfen: Teamdaten / Farben =====
    private Map<Integer, Team> teamMapById() {
        Game g = gameService.getGame();
        Map<Integer, Team> map = new HashMap<>();
        if (g == null || g.getFamilies() == null)
            return map;
        for (Family f : g.getFamilies()) {
            if (f == null || f.getTeams() == null)
                continue;
            for (Team t : f.getTeams()) {
                if (t != null && t.getId() != null)
                    map.put(t.getId(), t);
            }
        }
        return map;
    }

    private Team findTeamByName(Map<Integer, Team> byId, String name) {
        if (name == null)
            return null;
        for (Team t : byId.values()) {
            if (t != null && name.equals(t.getName()))
                return t;
        }
        return null;
    }

    private String teamToCssColor(Team t) {
        if (t == null)
            return null;
        Object sc = t.getColor(); // SerializableColor
        String fromSerializable = serializableColorToCss(sc);
        if (fromSerializable != null)
            return fromSerializable;

        if (t.getFamily() != null) {
            Object fc = t.getFamily().getColor();
            String fromFamily = serializableColorToCss(fc);
            if (fromFamily != null)
                return fromFamily;
        }
        return null;
    }

    private void rebuildPrestigeChart() {
        if (prestigeChart == null)
            return;
        Game g = gameService.getGame();
        if (g == null || g.getFamilies() == null) {
            prestigeChart.getData().clear();
            return;
        }

        // --- Signatur berechnen, um unnötige Updates zu vermeiden ---
        String sig = g.getFamilies().stream()
                .filter(Objects::nonNull)
                .map(f -> {
                    String fam = f.getName() == null ? "" : f.getName();
                    double sum = (f.getTeams() == null ? List.<Team>of() : f.getTeams())
                            .stream().filter(Objects::nonNull)
                            .mapToDouble(Team::getPrestige).sum();
                    return fam + ":" + String.format(Locale.ROOT, "%.4f", sum);
                })
                .collect(Collectors.joining("|"));

        if (sig.equals(lastPrestigeSignature)) {
            return; // nichts geändert
        }
        lastPrestigeSignature = sig;

        // --- Y-Kategorien = Familiennamen (ein Balken je Familie) ---
        List<Family> families = g.getFamilies();
        List<String> categories = families.stream()
                .map(f -> f != null && f.getName() != null ? f.getName() : "—")
                .toList();
        if (prestigeYAxis != null)
            prestigeYAxis.setCategories(javafx.collections.FXCollections.observableArrayList(categories));

        // --- Pro Team eine Series, Werte je Familie setzen (nur >0 anzeigen) ---
        prestigeChart.getData().clear();

        Map<Integer, Team> allTeams = new LinkedHashMap<>();
        for (Family f : families) {
            if (f == null || f.getTeams() == null)
                continue;
            for (Team t : f.getTeams()) {
                if (t != null && t.getId() != null)
                    allTeams.put(t.getId(), t);
            }
        }

        for (Team team : allTeams.values()) {
            String seriesName = team.getName() == null ? ("Team " + team.getId()) : team.getName();
            StackedBarChart.Series<Number, String> series = new StackedBarChart.Series<>();
            series.setName(seriesName);

            boolean anyData = false;
            for (Family fam : families) {
                if (fam == null)
                    continue;
                double v = 0.0;
                if (fam.getTeams() != null) {
                    for (Team ft : fam.getTeams()) {
                        if (ft != null && Objects.equals(ft.getId(), team.getId())) {
                            v = Math.max(0.0, ft.getPrestige());
                            break;
                        }
                    }
                }
                if (v > 0) {
                    String cat = fam.getName() == null ? "—" : fam.getName();
                    series.getData().add(new StackedBarChart.Data<>(v, cat));
                    anyData = true;
                }
            }

            if (anyData) {
                prestigeChart.getData().add(series);
            }
        }

        // --- Farben je Team anwenden (node kann erst nach Layout existieren) ---
        for (StackedBarChart.Series<Number, String> s : prestigeChart.getData()) {
            String teamName = s.getName();
            Team team = findTeamByName(allTeams, teamName);
            String cssColor = teamToCssColor(team);
            if (cssColor == null)
                continue;

            for (StackedBarChart.Data<Number, String> d : s.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-bar-fill: " + cssColor + ";");
                } else {
                    d.nodeProperty().addListener((obs, oldN, newN) -> {
                        if (newN != null)
                            newN.setStyle("-fx-bar-fill: " + cssColor + ";");
                    });
                }
            }
        }
    }

    /** SerializableColor → CSS "rgba(r,g,b,a)" (per Reflection) */
    private String serializableColorToCss(Object sc) {
        if (sc == null)
            return null;
        try {
            Method m = sc.getClass().getMethod("toJavaFXColor");
            Object col = m.invoke(sc);
            if (col instanceof Color c)
                return colorToCss(c);
        } catch (Exception ignored) {
        }

        try {
            Method mr = sc.getClass().getMethod("getRed");
            Method mg = sc.getClass().getMethod("getGreen");
            Method mb = sc.getClass().getMethod("getBlue");
            Double r = to01(mr.invoke(sc));
            Double g = to01(mg.invoke(sc));
            Double b = to01(mb.invoke(sc));
            double a = 1.0;
            try {
                Method ma = sc.getClass().getMethod("getOpacity");
                a = to01(ma.invoke(sc));
            } catch (Exception ignored) {
            }
            if (r != null && g != null && b != null) {
                return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                        (int) Math.round(r * 255),
                        (int) Math.round(g * 255),
                        (int) Math.round(b * 255),
                        a);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Double to01(Object val) {
        if (val == null)
            return null;
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (d > 1.0)
                return d / 255.0;
            if (d < 0.0)
                return 0.0;
            return d;
        }
        return null;
    }

    private String colorToCss(Color c) {
        if (c == null)
            return null;
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                c.getOpacity());
    }

    // ===== Materialien =====
    private List<String> buildMaterialsLines(BuildCategory bc) {
        Map<Material, Integer> need = bc.getNeededMaterials();
        Map<Material, Integer> pay = bc.getPayedMaterials();

        return Arrays.stream(Material.values())
                .map(m -> {
                    int n = need.getOrDefault(m, 0);
                    int p = pay.getOrDefault(m, 0);
                    if (n == 0 && p == 0)
                        return null;
                    return String.format("%d/%d %s", p, n, m.name());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void resizeMaterialsListToFitContent() {
        if (materialsList == null)
            return;
        double cell = materialsList.getFixedCellSize() > 0 ? materialsList.getFixedCellSize() : 24;
        int rows = (materialsList.getItems() != null) ? materialsList.getItems().size() : 0;
        double newHeight = rows * cell + 2;
        materialsList.setPrefHeight(newHeight);
        materialsList.setMinHeight(newHeight);
        materialsList.setMaxHeight(newHeight);
    }

    // ===== Auswahlhilfen =====
    private BuildCategory pickBuildCategoryByNames(Set<String> names) {
        if (names == null || names.isEmpty())
            return null;
        Game g = gameService.getGame();
        if (g == null || g.getCategories() == null)
            return null;

        List<BuildCategory> candidates = g.getCategories().stream()
                .filter(c -> c instanceof BuildCategory)
                .map(c -> (BuildCategory) c)
                .filter(bc -> names.contains(bc.getName())
                        || names.contains(safeGetDisplayName(bc)))
                .collect(Collectors.toList());

        if (candidates.isEmpty())
            return null;
        return candidates.get(0);
    }

    private String safeGetDisplayName(BuildCategory bc) {
        try {
            String dn = bc.getDisplayName();
            return dn == null ? "" : dn;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    // ===== Cleanup =====
    public void shutdown() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
    }
}

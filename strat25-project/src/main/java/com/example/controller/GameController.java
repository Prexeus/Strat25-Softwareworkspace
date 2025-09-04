package com.example.controller;

import com.example.model.*;
import com.example.service.GameService;
import com.example.view.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;


import java.util.*;
import java.util.stream.Collectors;

public class GameController {

    private final GameService gameService;
    private final SceneManager sceneManager;

    private Timeline uiTicker;

    public GameController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    // --- right column controls
    @FXML
    private Label gameNameLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label speedLabel;
    @FXML
    private TextField speedField;
    @FXML
    private Button applySpeedBtn;

    @FXML
    private Label multiplierLabel;
    @FXML
    private TextField multiplierField;
    @FXML
    private Button applyMultiplierBtn;

    @FXML
    private Button startBtn;
    @FXML
    private Button pauseBtn;
    @FXML
    private Button resumeBtn;
    @FXML
    private Button stopBtn;
    @FXML
    private Button saveBtn;

    @FXML
    private ComboBox<String> buildSelect; // Revolution / Versailles
    @FXML
    private Button nextPhaseBtn;

    @FXML
    private VBox catMultipliersBox;

    // center: team tabs
    @FXML
    private TabPane teamsTabs;

    // right: family prestige table
    @FXML
    private TableView<FamilyPrestigeRow> prestigeTable;
    @FXML
    private TableColumn<FamilyPrestigeRow, String> familyCol;
    @FXML
    private TableColumn<FamilyPrestigeRow, Number> prestigeCol;

    // ----- NEW: Calculator UI (left/bottom) -----
    @FXML
    private Label breadFactorLabel, housingFactorLabel, healthFactorLabel;
    @FXML
    private TextField breadFactorField, housingFactorField, healthFactorField;
    @FXML
    private Button applyBreadFactorBtn, applyHousingFactorBtn, applyHealthFactorBtn;

    @FXML
    private TextField trBreadField, trHouse1Field, trHouse2Field, trHealth1Field, trHealth2Field;
    @FXML
    private Button calcComputeBtn;
    @FXML
    private Label calcResultLabel;

    // Build categories (filtered)
    private List<BuildCategory> buildCategories = List.of();

    // category multiplier rows (right side)
    private final List<CategoryMultiplierRow> catMultiplierRows = new ArrayList<>();

    // per team tab state
    private final List<TeamTab> teamTabControls = new ArrayList<>();

    @FXML
    private void initialize() {
        Game g = gameService.getGame();
        if (g != null) {
            gameNameLabel.setText(g.getName());
            timeLabel.setText(formatSecondsFloor(g.getGameTime().getScaledSeconds()));
            speedLabel.setText(speedText(g.getGameTime().getGameSpeed()));
            speedField.setPromptText("1.0");
            speedField.clear();

            multiplierLabel.setText(multiplierText(g.getPrestigeMultiplier()));
            multiplierField.setPromptText("1.0");
            multiplierField.clear();
        }

        // lifecycle
        startBtn.setOnAction(e -> gameService.startGame());
        pauseBtn.setOnAction(e -> gameService.pauseGame());
        resumeBtn.setOnAction(e -> gameService.resumeGame());
        stopBtn.setOnAction(e -> {
            gameService.stopGame();
            sceneManager.showLauncher();
            if (uiTicker != null)
                uiTicker.stop();
        });

        // save (läuft auf Logic-Thread; UI via Platform.runLater)
        saveBtn.setOnAction(e -> gameService.runOnLogic(() -> {
            try {
                gameService.pauseGame();
                gameService.saveGame();
                Platform.runLater(() -> info("Game saved."));
            } catch (Exception ex) {
                Platform.runLater(() -> error("Saving failed:\n" + ex.getMessage()));
            } finally {
                gameService.resumeGame();
            }
        }));

        // speed / multiplier (Mutationen auf Logic-Thread)
        applySpeedBtn.setOnAction(e -> applySpeedFromField());
        speedField.setOnAction(e -> applySpeedFromField());
        applyMultiplierBtn.setOnAction(e -> applyMultiplierFromField());
        multiplierField.setOnAction(e -> applyMultiplierFromField());

        // family table
        familyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFamilyName()));
        prestigeCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().getPrestigeSum()));
        prestigeCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("%.2f", item.doubleValue()));
            }
        });

        // gather build categories
        buildCategories = findBuildCategories();
        buildSelect.getItems().setAll(
                buildCategories.stream().map(BuildCategory::getName).collect(Collectors.toList()));
        if (!buildCategories.isEmpty()) {
            buildSelect.getSelectionModel().select(0);
        }
        buildSelect.setOnAction(e -> refreshBuildSelectionInTabs());

        nextPhaseBtn.setOnAction(e -> {
            BuildCategory bc = getSelectedBuild();
            if (bc == null) {
                warn("Bitte zuerst ein Bauspiel rechts auswählen.");
                return;
            }
            gameService.runOnLogic(() -> {
                bc.nextConstructionPhase();
                Platform.runLater(() -> {
                    info("Nächste Bauetappe gestartet: " + bc.getName());
                    refreshBuildSelectionInTabs();
                });
            });
        });

        // --- NEW: Calculator wiring ---
        initCalculatorUi();

        // build tabs + first fill
        buildTeamTabs();
        refreshPrestigeTable();
        refreshBuildSelectionInTabs(); // sets header & rows

        // right: category multipliers UI
        buildCategoryMultiplierControls();

        // UI ticker (1s)
        if (uiTicker != null)
            uiTicker.stop();
        uiTicker = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            Game game = gameService.getGame();
            if (game != null) {
                timeLabel.setText(formatSecondsFloor(game.getGameTime().getScaledSeconds()));
                speedLabel.setText(speedText(game.getGameTime().getGameSpeed()));
                multiplierLabel.setText(multiplierText(game.getPrestigeMultiplier()));

                // update team tabs (influence + build section)
                for (TeamTab tt : teamTabControls) {
                    tt.prestigeValue.setText(String.format("%.2f", tt.team.getPrestige()));
                    for (InfluenceRow row : tt.influenceRows) {
                        updateInfluenceRow(row, tt.team, game);
                    }
                    updateBuildRows(tt);
                }

                // right: update category multiplier labels
                for (CategoryMultiplierRow cmr : catMultiplierRows) {
                    cmr.currentLabel.setText(multiplierText(cmr.category.getPrestigeMultiplier()));
                }

                // update family sums
                refreshPrestigeTable();
            }
        }));
        uiTicker.setCycleCount(Timeline.INDEFINITE);
        uiTicker.play();
    }

    // -------- Build Team Tabs (center) --------

    private void buildTeamTabs() {
        teamsTabs.getTabs().clear();
        teamTabControls.clear();

        Game g = gameService.getGame();
        if (g == null || g.getFamilies() == null)
            return;

        List<CategoryInterface> categories = g.getCategories();

        for (Family f : g.getFamilies()) {
            if (f.getTeams() == null)
                continue;
            for (Team t : f.getTeams()) {
                if (t == null)
                    continue;

                VBox root = new VBox(14);
                root.setPadding(new Insets(10));

                // Prestige row
                HBox prestigeRow = new HBox(8);
                Label prestigeLabel = new Label("Prestige:");
                Label prestigeValue = new Label(String.format("%.2f", t.getPrestige()));
                prestigeValue.setStyle("-fx-font-family: monospace;");
                TextField prestigeDeltaField = new TextField();
                prestigeDeltaField.setPromptText("+10 or -3");
                prestigeDeltaField.setPrefWidth(100);
                Button prestigeAddBtn = new Button("Add");
                prestigeRow.getChildren().addAll(prestigeLabel, prestigeValue, prestigeDeltaField, prestigeAddBtn);
                prestigeAddBtn.setOnAction(e -> applyTeamPrestigeDelta(t, prestigeDeltaField));
                prestigeDeltaField.setOnAction(e -> applyTeamPrestigeDelta(t, prestigeDeltaField));
                root.getChildren().add(prestigeRow);

                // Section header: Einfluss
                Label inflHdr = new Label("Einfluss");
                inflHdr.setStyle("-fx-font-weight: bold;");
                root.getChildren().add(inflHdr);

                // Influence grid
                GridPane inflGrid = new GridPane();
                inflGrid.setHgap(8);
                inflGrid.setVgap(6);
                inflGrid.setPadding(new Insets(4));

                int row = 0;
                inflGrid.add(styledSmall("Kategorie", true), 0, row);
                inflGrid.add(styledSmall("Einfluss", true), 1, row);
                inflGrid.add(styledSmall("Anteil", true), 2, row);
                inflGrid.add(new Label(""), 3, row);
                inflGrid.add(new Label(""), 4, row);
                row++;

                List<InfluenceRow> influenceRows = new ArrayList<>();
                if (categories != null) {
                    for (CategoryInterface c : categories) {
                        c.getInfluenceMap().putIfAbsent(t.getId(), 0.0);

                        Label catName = new Label(c.getName());
                        Label valLbl = new Label();
                        valLbl.setStyle("-fx-font-family: monospace;");
                        Label pctLbl = new Label();
                        pctLbl.setStyle("-fx-font-family: monospace;");
                        TextField deltaField = new TextField();
                        deltaField.setPromptText("+5 or -2");
                        deltaField.setPrefWidth(80);
                        Button addBtn = new Button("Add");

                        inflGrid.add(catName, 0, row);
                        inflGrid.add(valLbl, 1, row);
                        inflGrid.add(pctLbl, 2, row);
                        inflGrid.add(deltaField, 3, row);
                        inflGrid.add(addBtn, 4, row);

                        InfluenceRow ir = new InfluenceRow(c, valLbl, pctLbl, deltaField);
                        influenceRows.add(ir);

                        addBtn.setOnAction(e -> applyInfluenceDelta(t, ir));
                        deltaField.setOnAction(e -> applyInfluenceDelta(t, ir));

                        row++;
                    }
                }
                root.getChildren().add(inflGrid);

                // Build section
                Label buildHdr = new Label("Bau – (keine Auswahl)");
                buildHdr.setStyle("-fx-font-weight: bold;");
                root.getChildren().add(buildHdr);

                GridPane buildGrid = new GridPane();
                buildGrid.setHgap(8);
                buildGrid.setVgap(6);
                buildGrid.setPadding(new Insets(4));

                int mrow = 0;
                buildGrid.add(styledSmall("Rohstoff", true), 0, mrow);
                buildGrid.add(styledSmall("Status", true), 1, mrow);
                buildGrid.add(new Label(""), 2, mrow);
                buildGrid.add(new Label(""), 3, mrow);
                mrow++;

                List<MaterialRow> materialRows = new ArrayList<>();
                for (Material mat : Material.values()) {
                    Label matName = new Label(mat.name());
                    Label status = new Label();
                    status.setStyle("-fx-font-family: monospace;");
                    TextField amount = new TextField();
                    amount.setPromptText("2");
                    amount.setPrefWidth(70);
                    Button add = new Button("Add");

                    buildGrid.add(matName, 0, mrow);
                    buildGrid.add(status, 1, mrow);
                    buildGrid.add(amount, 2, mrow);
                    buildGrid.add(add, 3, mrow);

                    MaterialRow mr = new MaterialRow(mat, status, amount);
                    materialRows.add(mr);

                    add.setOnAction(e -> applyMaterialDelta(t, mr));
                    amount.setOnAction(e -> applyMaterialDelta(t, mr));

                    mrow++;
                }
                root.getChildren().add(buildGrid);

                Tab tab = new Tab(t.getName(), root);
                tab.setClosable(false);
                teamsTabs.getTabs().add(tab);

                teamTabControls.add(new TeamTab(t, prestigeValue, prestigeDeltaField, influenceRows,
                        buildHdr, materialRows));
            }
        }

        if (!teamsTabs.getTabs().isEmpty()) {
            teamsTabs.getSelectionModel().select(0);
        }

        // --- NEW: Beim Team-Wechsel TR-Eingaben resetten ---
        teamsTabs.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> resetCalculatorInputs());
    }

    // -------- Actions (Mutationen via Logic-Thread) --------

    private void applyTeamPrestigeDelta(Team team, TextField deltaField) {
        String txt = deltaField.getText();
        if (txt == null || txt.isBlank()) {
            warn("Enter a number like 12 or -30.");
            return;
        }
        try {
            double v = Double.parseDouble(txt.replace(',', '.'));
            gameService.runOnLogic(() -> team.addPrestige(v));
            deltaField.clear();
        } catch (NumberFormatException nfe) {
            warn("Invalid number. Examples: 12, -30, 3.5");
        }
    }

    private void applyInfluenceDelta(Team team, InfluenceRow row) {
        String txt = row.deltaField.getText();
        if (txt == null || txt.isBlank()) {
            warn("Enter a number like 5 or -2.");
            return;
        }
        try {
            double v = Double.parseDouble(txt.replace(',', '.'));
            gameService.runOnLogic(() -> {
                Map<Integer, Double> map = row.category.getInfluenceMap();
                if (!map.containsKey(team.getId()))
                    map.put(team.getId(), 0.0);
                row.category.addInfluence(team, v);
            });
            row.deltaField.clear();
        } catch (NumberFormatException nfe) {
            warn("Invalid number. Examples: 5, -2, 1.25");
        }
    }

    private void applyMaterialDelta(Team team, MaterialRow row) {
        BuildCategory bc = getSelectedBuild();
        if (bc == null) {
            warn("Bitte zuerst ein Bauspiel rechts auswählen.");
            return;
        }
        String txt = row.amountField.getText();
        if (txt == null || txt.isBlank()) {
            warn("Bitte eine Menge eingeben (z. B. 2).");
            return;
        }
        int v;
        try {
            v = Integer.parseInt(txt.trim());
            if (v <= 0) {
                warn("Nur positive Mengen einzahlen.");
                return;
            }
        } catch (NumberFormatException nfe) {
            warn("Ungültige Zahl. Beispiel: 2");
            return;
        }

        gameService.runOnLogic(() -> {
            int free = calcFree(bc, row.material);
            if (free <= 0) {
                Platform.runLater(() -> warn("Für " + row.material.name() + " wird aktuell nichts mehr benötigt."));
                return;
            }
            if (v > free) {
                int max = free;
                Platform.runLater(() -> warn("Maximal " + max + " einzahlbar (noch frei)."));
                return;
            }
            bc.addMaterial(team, row.material, v);
            Platform.runLater(row.amountField::clear);
        });
    }

    private int calcFree(BuildCategory bc, Material material) {
        Map<Material, Integer> need = bc.getNeededMaterials();
        Map<Material, Integer> pay = bc.getPayedMaterials();
        int n = need.getOrDefault(material, 0);
        int p = pay.getOrDefault(material, 0);
        return Math.max(n - p, 0);
    }

    private void applySpeedFromField() {
        Game g = gameService.getGame();
        if (g == null)
            return;
        String txt = speedField.getText();
        if (txt == null || txt.isBlank()) {
            warn("Please enter a positive number for the game speed (e.g., 1.0, 2.0, 0.5).");
            return;
        }
        try {
            double v = Double.parseDouble(txt.replace(',', '.'));
            if (v <= 0)
                throw new IllegalArgumentException("Speed must be > 0");
            gameService.runOnLogic(() -> g.getGameTime().setGameSpeed(v));
            speedField.clear();
        } catch (Exception ex) {
            warn("Invalid speed. Use a positive number (e.g., 1.0, 2.0, 0.5).");
        }
    }

    private void applyMultiplierFromField() {
        Game g = gameService.getGame();
        if (g == null)
            return;
        String txt = multiplierField.getText();
        if (txt == null || txt.isBlank()) {
            warn("Please enter a positive number for the prestige multiplier (e.g., 1.0, 2.0, 0.5).");
            return;
        }
        try {
            double v = Double.parseDouble(txt.replace(',', '.'));
            if (v <= 0)
                throw new IllegalArgumentException("Multiplier must be > 0");
            gameService.runOnLogic(() -> g.setPrestigeMultiplier(v));
            multiplierField.clear();
        } catch (Exception ex) {
            warn("Invalid multiplier. Use a positive number (e.g., 1.0, 2.0, 0.5).");
        }
    }

    // -------- Updates --------

    private void updateInfluenceRow(InfluenceRow row, Team team, Game game) {
        if (row == null || team == null || game == null)
            return;
        Map<Integer, Double> map = row.category.getInfluenceMap();
        if (!map.containsKey(team.getId()))
            map.put(team.getId(), 0.0);
        double own = map.getOrDefault(team.getId(), 0.0);
        double total = 0.0;
        for (double v : map.values())
            total += v;
        double pct = (total > 0.0) ? (own / total) * 100.0 : 0.0;

        row.valueLabel.setText(String.format("%.2f", own));
        row.percentLabel.setText(String.format("%.1f%%", pct));
    }

    private void refreshBuildSelectionInTabs() {
        BuildCategory bc = getSelectedBuild();
        String hdr = (bc == null) ? "Bau – (keine Auswahl)" : "Bau – " + bc.getName();
        for (TeamTab tt : teamTabControls) {
            tt.buildHeader.setText(hdr);
            updateBuildRows(tt);
        }
    }

    private void updateBuildRows(TeamTab tt) {
        BuildCategory bc = getSelectedBuild();
        if (bc == null) {
            for (MaterialRow mr : tt.materialRows) {
                mr.statusLabel.setText("—/— -> —");
            }
            return;
        }
        for (MaterialRow mr : tt.materialRows) {
            updateSingleMaterialRow(mr, bc);
        }
    }

    private void updateSingleMaterialRow(MaterialRow mr, BuildCategory bc) {
        Map<Material, Integer> need = bc.getNeededMaterials();
        Map<Material, Integer> pay = bc.getPayedMaterials();
        int n = need.getOrDefault(mr.material, 0);
        int p = pay.getOrDefault(mr.material, 0);
        int free = Math.max(n - p, 0);
        mr.statusLabel.setText(String.format("%d/%d -> %d", p, n, free));
    }

    // -------- right: category multipliers --------

    private void buildCategoryMultiplierControls() {
        catMultipliersBox.getChildren().clear();
        catMultiplierRows.clear();

        Game g = gameService.getGame();
        if (g == null || g.getCategories() == null)
            return;

        for (CategoryInterface ci : g.getCategories()) {
            HBox row = new HBox(8);
            Label name = new Label(ci.getName());
            Label cur = new Label(multiplierText(ci.getPrestigeMultiplier()));
            cur.setStyle("-fx-font-family: monospace;");
            TextField inp = new TextField();
            inp.setPromptText("1.0");
            inp.setPrefWidth(80);
            Button apply = new Button("Apply");

            row.getChildren().addAll(name, cur, inp, apply);
            catMultipliersBox.getChildren().add(row);

            CategoryMultiplierRow cmr = new CategoryMultiplierRow(ci, cur, inp);
            catMultiplierRows.add(cmr);

            apply.setOnAction(e -> applyCategoryMultiplier(cmr));
            inp.setOnAction(e -> applyCategoryMultiplier(cmr));
        }
    }

    private void applyCategoryMultiplier(CategoryMultiplierRow cmr) {
        String txt = cmr.input.getText();
        if (txt == null || txt.isBlank()) {
            warn("Bitte einen positiven Multiplikator eingeben (z. B. 1.0, 2.0, 0.5).");
            return;
        }
        try {
            double v = Double.parseDouble(txt.replace(',', '.'));
            if (v <= 0)
                throw new IllegalArgumentException("Multiplier must be > 0");
            gameService.runOnLogic(() -> cmr.category.setPrestigeMultiplier(v));
            cmr.input.clear();
        } catch (Exception ex) {
            warn("Ungültiger Multiplikator. Beispiele: 1.0, 2.0, 0.5");
        }
    }

    // -------- Calculator helpers --------

    private void initCalculatorUi() {
        BackboneCalculator calc = getCalc();
        if (calc != null) {
            breadFactorLabel.setText(formatFactor(calc.getBreadFactor()));
            housingFactorLabel.setText(formatFactor(calc.getHousingFactor()));
            healthFactorLabel.setText(formatFactor(calc.getHealthFactor()));
        }
        // Apply handlers for factors
        applyBreadFactorBtn.setOnAction(e -> applyBreadFactor());
        breadFactorField.setOnAction(e -> applyBreadFactor());

        applyHousingFactorBtn.setOnAction(e -> applyHousingFactor());
        housingFactorField.setOnAction(e -> applyHousingFactor());

        applyHealthFactorBtn.setOnAction(e -> applyHealthFactor());
        healthFactorField.setOnAction(e -> applyHealthFactor());

        // Compute handlers for TR inputs
        calcComputeBtn.setOnAction(e -> recalcCalculator());
        trBreadField.setOnAction(e -> recalcCalculator());
        trHouse1Field.setOnAction(e -> recalcCalculator());
        trHouse2Field.setOnAction(e -> recalcCalculator());
        trHealth1Field.setOnAction(e -> recalcCalculator());
        trHealth2Field.setOnAction(e -> recalcCalculator());

        // Startzustand: leere TR-Felder & kein Ergebnis
        resetCalculatorInputs();
    }

    private void applyBreadFactor() {
        String t = breadFactorField.getText();
        if (t == null || t.isBlank()) {
            warn("Bitte Faktor für Brot eingeben (z. B. 1.5).");
            return;
        }
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            gameService.runOnLogic(() -> getCalc().setBreadFactor(v));
            breadFactorLabel.setText(formatFactor(v));
            breadFactorField.clear();
            recalcCalculator();
        } catch (NumberFormatException ex) {
            warn("Ungültiger Brot-Faktor (z. B. 1.5).");
        }
    }

    private void applyHousingFactor() {
        String t = housingFactorField.getText();
        if (t == null || t.isBlank()) {
            warn("Bitte Faktor für Haus eingeben (z. B. 1.0).");
            return;
        }
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            gameService.runOnLogic(() -> getCalc().setHousingFactor(v));
            housingFactorLabel.setText(formatFactor(v));
            housingFactorField.clear();
            recalcCalculator();
        } catch (NumberFormatException ex) {
            warn("Ungültiger Haus-Faktor (z. B. 1.0).");
        }
    }

    private void applyHealthFactor() {
        String t = healthFactorField.getText();
        if (t == null || t.isBlank()) {
            warn("Bitte Faktor für Krank eingeben (z. B. 0.75).");
            return;
        }
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            gameService.runOnLogic(() -> getCalc().setHealthFactor(v));
            healthFactorLabel.setText(formatFactor(v));
            healthFactorField.clear();
            recalcCalculator();
        } catch (NumberFormatException ex) {
            warn("Ungültiger Krank-Faktor (z. B. 0.75).");
        }
    }

    private void resetCalculatorInputs() {
        if (trBreadField == null)
            return; // falls FXML noch nicht geladen
        trBreadField.clear();
        trHouse1Field.clear();
        trHouse2Field.clear();
        trHealth1Field.clear();
        trHealth2Field.clear();
        calcResultLabel.setText("—");
    }

    private void recalcCalculator() {
        BackboneCalculator calc = getCalc();
        if (calc == null) {
            calcResultLabel.setText("—");
            return;
        }

        int bread = parseIntOrZero(trBreadField.getText());
        int h1 = parseIntOrZero(trHouse1Field.getText());
        int h2 = parseIntOrZero(trHouse2Field.getText());
        int kr1 = parseIntOrZero(trHealth1Field.getText());
        int kr2 = parseIntOrZero(trHealth2Field.getText());

        double res = calc.calculateBackboneInfluence(bread, h1, h2, kr1, kr2);
        String resText = String.format("%.2f", res);

        // UI-Label aktualisieren
        calcResultLabel.setText(resText);

        // --- NEU: automatisch in die Zwischenablage kopieren ---
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(resText);
            Clipboard.getSystemClipboard().setContent(content);
            // Optional: kleines optisches Feedback? (z.B. Tooltip oder Status-Label)
        } catch (Exception ignore) {
            // Falls auf manchen Plattformen nicht erlaubt/verfügbar: still schlucken
        }
    }

    private int parseIntOrZero(String t) {
        if (t == null || t.isBlank())
            return 0;
        try {
            return Integer.parseInt(t.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String formatFactor(double v) {
        return String.format("× %.2f", v);
    }

    private BackboneCalculator getCalc() {
        Game g = gameService.getGame();
        return (g == null) ? null : g.getBackboneCalculator();
    }

    // -------- helpers --------

    private List<BuildCategory> findBuildCategories() {
        Game g = gameService.getGame();
        if (g == null || g.getCategories() == null)
            return List.of();
        List<BuildCategory> list = new ArrayList<>();
        for (CategoryInterface ci : g.getCategories()) {
            if (ci instanceof BuildCategory bc)
                list.add(bc);
        }
        return list;
    }

    private BuildCategory getSelectedBuild() {
        String name = buildSelect.getSelectionModel().getSelectedItem();
        if (name == null)
            return null;
        for (BuildCategory bc : buildCategories) {
            if (name.equals(bc.getName()))
                return bc;
        }
        return null;
    }

    private void refreshPrestigeTable() {
        Game g = gameService.getGame();
        if (g == null)
            return;

        List<FamilyPrestigeRow> rows = new ArrayList<>();
        if (g.getFamilies() != null) {
            for (Family f : g.getFamilies()) {
                double sum = 0.0;
                if (f.getTeams() != null) {
                    for (Team t : f.getTeams())
                        sum += (t != null ? t.getPrestige() : 0.0);
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

    private static Label styledSmall(String text, boolean bold) {
        Label l = new Label(text);
        StringBuilder sb = new StringBuilder("-fx-font-size: 11;");
        if (bold)
            sb.append(" -fx-font-weight: bold;");
        l.setStyle(sb.toString());
        return l;
    }

    private static String speedText(double v) {
        return String.format("(%.2fx)", v);
    }

    private static String multiplierText(double v) {
        return String.format("(%.2fx)", v);
    }

    private static String formatSecondsFloor(double scaledSeconds) {
        long s = (long) Math.floor(scaledSeconds);
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static void info(String msg) {
        show(Alert.AlertType.INFORMATION, "Info", msg);
    }

    private static void warn(String msg) {
        show(Alert.AlertType.WARNING, "Warning", msg);
    }

    private static void error(String msg) {
        show(Alert.AlertType.ERROR, "Error", msg);
    }

    private static void show(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // table row for families
    public static class FamilyPrestigeRow {
        private final String familyName;
        private final double prestigeSum;

        public FamilyPrestigeRow(String familyName, double prestigeSum) {
            this.familyName = familyName;
            this.prestigeSum = prestigeSum;
        }

        public String getFamilyName() {
            return familyName;
        }

        public double getPrestigeSum() {
            return prestigeSum;
        }
    }

    // per-team tab bundle
    private static class TeamTab {
        final Team team;
        final Label prestigeValue;
        final TextField prestigeDeltaField;
        final List<InfluenceRow> influenceRows;

        final Label buildHeader; // "Bau – {Name}"
        final List<MaterialRow> materialRows;

        TeamTab(Team team,
                Label prestigeValue,
                TextField prestigeDeltaField,
                List<InfluenceRow> influenceRows,
                Label buildHeader,
                List<MaterialRow> materialRows) {
            this.team = team;
            this.prestigeValue = prestigeValue;
            this.prestigeDeltaField = prestigeDeltaField;
            this.influenceRows = influenceRows;
            this.buildHeader = buildHeader;
            this.materialRows = materialRows;
        }
    }

    // one category (influence) row per team
    private static class InfluenceRow {
        final CategoryInterface category;
        final Label valueLabel;
        final Label percentLabel;
        final TextField deltaField;

        InfluenceRow(CategoryInterface category, Label valueLabel, Label percentLabel, TextField deltaField) {
            this.category = category;
            this.valueLabel = valueLabel;
            this.percentLabel = percentLabel;
            this.deltaField = deltaField;
        }
    }

    // one material row (build)
    private static class MaterialRow {
        final Material material;
        final Label statusLabel; // "gezahlt/benötigt -> frei"
        final TextField amountField;

        MaterialRow(Material material, Label statusLabel, TextField amountField) {
            this.material = material;
            this.statusLabel = statusLabel;
            this.amountField = amountField;
        }
    }

    // right: category multiplier row
    private static class CategoryMultiplierRow {
        final CategoryInterface category;
        final Label currentLabel;
        final TextField input;

        CategoryMultiplierRow(CategoryInterface category, Label currentLabel, TextField input) {
            this.category = category;
            this.currentLabel = currentLabel;
            this.input = input;
        }
    }
}

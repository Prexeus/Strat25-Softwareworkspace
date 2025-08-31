package com.example.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildCategory implements CategoryInterface, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private int constructionPhase = 0;

    private double prestigeMultiplier = 1.5;

    private final Map<Material, Integer> neededMaterialsMap = new EnumMap<>(Material.class);
    private final Map<Material, Integer> payedMaterialsMap  = new EnumMap<>(Material.class);
    private final Map<Integer, Double>   influenceMap       = new ConcurrentHashMap<>();

    private final Map<Material, Double> materialWorths; // serialisierbar (HashMap/EnumMap)
    private final String resourcePath;                  // z.B. "/com/example/csv/revolution.csv"
    private transient Path csvPath;                     // NICHT serialisieren

    public BuildCategory(String name,
                         Collection<Team> teams,
                         String resourcePath,
                         Map<Material, Double> materialWorths) {
        this(name, teams, resourcePath, null, materialWorths);
    }

    public BuildCategory(String name,
                         Collection<Team> teams,
                         String resourcePath,
                         Path csvPath,
                         Map<Material, Double> materialWorths) {
        this.name = name;
        this.resourcePath = resourcePath;
        this.csvPath = csvPath; // transient
        this.materialWorths = new HashMap<>(materialWorths);
        for (Team t : teams) influenceMap.put(t.getId(), 0.0);
        resetMaterialMapsToZero();
    }

    @Override public String getName() { return name; }

    @Override public Map<Integer, Double> getInfluenceMap() { return influenceMap; }

    @Override public void addInfluence(Team team, double influence) {
        influenceMap.merge(team.getId(), influence, Double::sum);
    }

    @Override public double getPrestigeMultiplier() {
        return prestigeMultiplier;
    }

    @Override public void setPrestigeMultiplier(double prestigeMultiplier) {
        this.prestigeMultiplier = prestigeMultiplier;
    }

    public Map<Material, Integer> getNeededMaterials() { return Collections.unmodifiableMap(neededMaterialsMap); }
    public Map<Material, Integer> getPayedMaterials()  { return Collections.unmodifiableMap(payedMaterialsMap);  }

    public void addMaterial(Team team, Material material, int amount) {
        payedMaterialsMap.merge(material, amount, Integer::sum);
        double w = materialWorths.getOrDefault(material, 0.0);
        influenceMap.merge(team.getId(), w * amount, Double::sum);
    }

    public void nextConstructionPhase() {
        constructionPhase++;
        resetMaterialMapsToZero();
        String[] row = readCsvRow(constructionPhase);
        if (row == null) return;

        Material[] mats = Material.values();
        for (int i = 0; i < mats.length; i++) {
            int col = 1 + i; // ab Spalte 1
            int val = 0;
            if (col < row.length) {
                String cell = row[col].trim();
                if (!cell.isEmpty()) {
                    try { val = Integer.parseInt(cell); }
                    catch (NumberFormatException e1) {
                        try { val = (int)Math.round(Double.parseDouble(cell.replace(',', '.'))); }
                        catch (NumberFormatException ignored) { val = 0; }
                    }
                }
            }
            neededMaterialsMap.put(mats[i], val);
        }
    }

    private void resetMaterialMapsToZero() {
        neededMaterialsMap.clear(); payedMaterialsMap.clear();
        for (Material m : Material.values()) {
            neededMaterialsMap.put(m, 0);
            payedMaterialsMap.put(m, 0);
        }
    }

    private String[] readCsvRow(int oneBasedRowIndex) {
        if (oneBasedRowIndex <= 0) return null;

        // 1) expliziter Pfad (zur Laufzeit, nicht persistiert)
        if (csvPath != null) {
            try (var br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                return readRowFromReader(br, oneBasedRowIndex);
            } catch (Exception ignored) { /* fallback */ }
        }
        // 2) Classpath-Resource (persistierter resourcePath)
        if (resourcePath != null) {
            try (var in = getClass().getResourceAsStream(resourcePath)) {
                if (in == null) return null;
                try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    return readRowFromReader(br, oneBasedRowIndex);
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String[] readRowFromReader(BufferedReader br, int oneBasedRowIndex) throws Exception {
        String line; int row = 0;
        while ((line = br.readLine()) != null) {
            if (line.isBlank()) continue;
            if (++row == oneBasedRowIndex) return line.split(",", -1);
        }
        return null;
    }
}

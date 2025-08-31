package com.example.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BuildCategory implements CategoryInterface {

    private static final String DEFAULT_RESOURCE = "/com/example/csv/revolution.csv";

    private final String name;
    private int constructionPhase = 0;

    private final HashMap<Material, Integer> neededMaterialsMap = new HashMap<>();
    private final HashMap<Material, Integer> payedMaterialsMap  = new HashMap<>();
    private final HashMap<Integer, Double> influenceMap         = new HashMap<>();

    private final Map<Material, Double> materialWorths; // z.B. Holz=0.1, Stein=0.3, Eisen=0.5, Gold=1.0

    private double prestigeMultiplier = 1.5;

    // optional: entweder Classpath-Resource nutzen oder ein konkreter Pfad
    private final Path csvPath; // kann null sein -> dann wird DEFAULT_RESOURCE (Classpath) benutzt

    public BuildCategory(String name, Collection<Team> teams, Path csvPath, Map<Material, Double> materialWorths) {
        this.name = name;
        this.csvPath = csvPath;
        this.materialWorths = materialWorths;
        for (Team team : teams) {
            influenceMap.put(team.getId(), 0.0);
        }
        resetMaterialMapsToZero(); // Initialzustand
    }

    // -------- CategoryInterface --------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void addInfluence(Team team, double influence) {
        influenceMap.put(team.getId(), influenceMap.get(team.getId()) + influence);
    }

    @Override
    public HashMap<Integer, Double> getInfluenceMap() {
        return influenceMap;
    }

    @Override
    public double getPrestigeMultiplier() {
        return prestigeMultiplier;
    }

    @Override
    public void setPrestigeMultiplier(double prestigeMultiplier) {
        this.prestigeMultiplier = prestigeMultiplier;
    }

    // -------- Materials / Phases --------

    public Map<Material, Integer> getNeededMaterials() { return Collections.unmodifiableMap(neededMaterialsMap); }
    public Map<Material, Integer> getPayedMaterials()  { return Collections.unmodifiableMap(payedMaterialsMap);  }

    public void addMaterial(Team team, Material material, int amount) {
        payedMaterialsMap.put(material, payedMaterialsMap.getOrDefault(material, 0) + amount);
        double worth = materialWorths.getOrDefault(material, 0.0);
        influenceMap.put(team.getId(), influenceMap.getOrDefault(team.getId(), 0.0) + worth * amount);
    }

    /**
     * Inkrementiert die Bauphase und lädt die passenden Anforderungen aus der CSV.
     * Phase 1 -> erste Zeile der CSV; Phase 2 -> zweite Zeile; usw.
     * Leere CSV-Felder bedeuten: 0 / nicht benötigt.
     * Vor dem Setzen werden both Maps (needed/payed) auf 0 zurückgesetzt.
     */
    public void nextConstructionPhase() {
        constructionPhase++;

        // 1) Beide Maps zurücksetzen
        resetMaterialMapsToZero();

        // 2) CSV-Zeile für die aktuelle Phase laden (1-basierter Index)
        String[] row = readCsvRow(constructionPhase);
        if (row == null) {
            // keine weitere Phase vorhanden -> einfach nichts setzen
            return;
        }

        // Erwartung: row[0] = Etappenname (ignorieren); ab col=1 folgen Materialien in ENUM-Reihenfolge
        Material[] mats = Material.values();
        for (int i = 0; i < mats.length; i++) {
            int colIndex = 1 + i; // ab Spalte 1
            int value = 0;
            if (colIndex < row.length) {
                String cell = row[colIndex].trim();
                if (!cell.isEmpty()) {
                    try {
                        value = Integer.parseInt(cell);
                    } catch (NumberFormatException ignored) {
                        // bei "7000 " oder ähnlichem: nochmal versuchen
                        try {
                            value = (int) Math.round(Double.parseDouble(cell.replace(',', '.')));
                        } catch (NumberFormatException ignored2) {
                            value = 0; // invalid -> als 0 behandeln
                        }
                    }
                }
            }
            neededMaterialsMap.put(mats[i], value);
            // payed bleibt (wie gefordert) zunächst 0 für alle
        }
    }

    // -------- internals --------

    private void resetMaterialMapsToZero() {
        neededMaterialsMap.clear();
        payedMaterialsMap.clear();
        for (Material m : Material.values()) {
            neededMaterialsMap.put(m, 0);
            payedMaterialsMap.put(m, 0);
        }
    }

    /**
     * Liest die CSV und gibt die gewünschte 1-basierte Zeile zurück (als Felder-Array),
     * oder null, wenn es diese Zeile nicht gibt.
     */
    private String[] readCsvRow(int oneBasedRowIndex) {
        if (oneBasedRowIndex <= 0) return null;

        // 1) Versuche expliziten Pfad
        if (csvPath != null) {
            try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                return readRowFromReader(br, oneBasedRowIndex);
            } catch (Exception e) {
                // Fallback auf Classpath
            }
        }

        // 2) Classpath-Ressource
        try (var in = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) return null;
            try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return readRowFromReader(br, oneBasedRowIndex);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] readRowFromReader(BufferedReader br, int oneBasedRowIndex) throws Exception {
        String line;
        int current = 0;
        while ((line = br.readLine()) != null) {
            // skip komplett leere Zeilen
            if (line.isBlank()) continue;
            current++;
            if (current == oneBasedRowIndex) {
                // -1 => Trailing-Empty-Fields behalten
                return line.split(",", -1);
            }
        }
        return null;
    }
}

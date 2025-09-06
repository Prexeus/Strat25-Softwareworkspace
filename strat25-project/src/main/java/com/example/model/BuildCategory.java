package com.example.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildCategory implements CategoryInterface, Serializable {
    private static final long serialVersionUID = 1L;

    // Basis
    private final String name;
    private final String fullName;           // NEU: ausführlicher Anzeigename
    private int constructionPhase = 0;

    private double prestigeMultiplier = 1.5;

    // Materialbedarf / Einzahlungen
    private final Map<Material, Integer> neededMaterialsMap = new EnumMap<>(Material.class);
    private final Map<Material, Integer> payedMaterialsMap  = new EnumMap<>(Material.class);

    // Einfluss pro Team (Team-ID -> Punkte)
    private final Map<Integer, Double> influenceMap = new ConcurrentHashMap<>();

    // Wertigkeiten (serialisierbar)
    private final Map<Material, Double> materialWorths;

    // CSV-Quelle
    private final String resourcePath;   // z.B. "/com/example/csv/revolution.csv"  (persistiert)
    private transient Path csvPath;      // Dateisystempfad (nicht persistiert)

    // Bilder-Basisordner für Phasen (persistiert)
    private final String imagesResourceBase; // z.B. "/com/example/images/revolution"
    private transient Path imagesDirPath;    // Dateisystem-Ordner (nicht persistiert)

    // OPTIONAL: Kategoriensymbol / Titelbild (persistiert)
    // Kann ein Classpath-Resource-Pfad ("/.../icon.png"), eine absolute URL ("https://...") oder ein Dateisystempfad sein.
    private final String imageUrlSpec;

    // aktueller Etappen-Titel (aus Spalte 0)
    private String currentPhaseTitle = null;

    // --------- Öffentliche Konstruktoren (Rückwärts-kompatibel) ---------

    /** Alter bequemer Konstruktor (ohne fullName) – delegiert auf den neuen und setzt fullName = name. */
    public BuildCategory(String name,
                         Collection<Team> teams,
                         String csvResourcePath,
                         Map<Material, Double> materialWorths,
                         String imagesResourceBase,
                         String imageUrlSpec) {
        this(name, name, teams, csvResourcePath, null, materialWorths, imagesResourceBase, null, imageUrlSpec);
    }

    /** Alter Voll-Konstruktor (ohne fullName) – delegiert auf den neuen und setzt fullName = name. */
    public BuildCategory(String name,
                         Collection<Team> teams,
                         String csvResourcePath,
                         Path csvFilePath,
                         Map<Material, Double> materialWorths,
                         String imagesResourceBase,
                         Path imagesDirPath,
                         String imageUrlSpec) {
        this(name, name, teams, csvResourcePath, csvFilePath, materialWorths, imagesResourceBase, imagesDirPath, imageUrlSpec);
    }

    // --------- NEU: Konstruktoren mit fullName ---------

    /** Neuer bequemer Konstruktor (Classpath-CSV). */
    public BuildCategory(String name,
                         String fullName,
                         Collection<Team> teams,
                         String csvResourcePath,
                         Map<Material, Double> materialWorths,
                         String imagesResourceBase,
                         String imageUrlSpec) {
        this(name, fullName, teams, csvResourcePath, null, materialWorths, imagesResourceBase, null, imageUrlSpec);
    }

    /** Neuer Voll-Konstruktor (Dateisystem-CSV optional). */
    public BuildCategory(String name,
                         String fullName,
                         Collection<Team> teams,
                         String csvResourcePath,
                         Path csvFilePath,
                         Map<Material, Double> materialWorths,
                         String imagesResourceBase,
                         Path imagesDirPath,
                         String imageUrlSpec) {
        this.name = Objects.requireNonNull(name, "name");
        this.fullName = (fullName == null || fullName.isBlank()) ? name : fullName;

        this.resourcePath = csvResourcePath;
        this.csvPath = csvFilePath; // transient

        this.materialWorths = new HashMap<>(Objects.requireNonNull(materialWorths, "materialWorths"));
        this.imagesResourceBase = imagesResourceBase;
        this.imagesDirPath = imagesDirPath; // transient
        this.imageUrlSpec = imageUrlSpec;

        for (Team t : teams) {
            influenceMap.put(t.getId(), 0.0);
        }
        resetMaterialMapsToZero();
    }

    // --------- CategoryInterface ---------

    @Override public String getName() { return name; }
    /** Nicht im Interface – darf aber zusätzlich angeboten werden. */
    public String getFullName() { return fullName; }

    @Override public Map<Integer, Double> getInfluenceMap() { return influenceMap; }

    @Override public void addInfluence(Team team, double influence) {
        influenceMap.merge(team.getId(), influence, Double::sum);
    }

    @Override public double getPrestigeMultiplier() { return prestigeMultiplier; }
    @Override public void setPrestigeMultiplier(double prestigeMultiplier) { this.prestigeMultiplier = prestigeMultiplier; }

    /** Optionales statisches Kategorienbild (z. B. für Listen/Icons). */
    @Override
    public Optional<URL> getImageUrl() {
        if (imageUrlSpec == null || imageUrlSpec.isBlank()) return Optional.empty();

        // 1) Classpath-Ressource (wenn z. B. "/com/example/..." übergeben wurde)
        try {
            URL cp = getClass().getResource(imageUrlSpec);
            if (cp != null) return Optional.of(cp);
        } catch (Exception ignored) {}

        // 2) Absolute URL (http/https/file etc.)
        try {
            return Optional.of(new URL(imageUrlSpec));
        } catch (Exception ignored) {}

        // 3) Lokaler Dateipfad
        try {
            Path p = Path.of(imageUrlSpec);
            if (Files.exists(p)) return Optional.of(p.toUri().toURL());
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    // --------- Public API ---------

    public int getConstructionPhase() { return constructionPhase; }

    /** Etappen-Titel der aktuellen Phase (aus CSV-Spalte 0). */
    public String getCurrentPhaseTitle() { return currentPhaseTitle; }

    /** Etappen-Titel einer beliebigen Phase (1-basiert), on-demand aus CSV gelesen. */
    public String getPhaseTitle(int oneBasedPhase) {
        String[] row = readCsvRow(oneBasedPhase);
        return (row != null && row.length > 0) ? row[0].trim() : null;
    }

    public Map<Material, Integer> getNeededMaterials() { return Collections.unmodifiableMap(neededMaterialsMap); }
    public Map<Material, Integer> getPayedMaterials()  { return Collections.unmodifiableMap(payedMaterialsMap);  }

    public void addMaterial(Team team, Material material, int amount) {
        payedMaterialsMap.merge(material, amount, Integer::sum);
        double w = materialWorths.getOrDefault(material, 0.0);
        influenceMap.merge(team.getId(), w * amount, Double::sum);
    }

    /**
     * Startet die nächste Bauetappe:
     * - setzt beide Material-Maps auf 0
     * - liest Zeile {constructionPhase} (1-basiert)
     * - übernimmt Spalte 0 als Etappen-Titel
     * - setzt Materialbedarfe aus den Folgespalten
     */
    public void nextConstructionPhase() {
        constructionPhase++;
        resetMaterialMapsToZero();

        String[] row = readCsvRow(constructionPhase);
        if (row == null) {
            currentPhaseTitle = null;
            return;
        }

        // Spalte 0 = Etappenname
        currentPhaseTitle = row.length > 0 ? row[0].trim() : null;

        // Ab Spalte 1 folgen die Materialien in ENUM-Reihenfolge
        Material[] mats = Material.values();
        for (int i = 0; i < mats.length; i++) {
            int col = 1 + i;
            int val = 0;
            if (col < row.length) {
                String cell = row[col].trim();
                if (!cell.isEmpty()) {
                    try {
                        val = Integer.parseInt(cell);
                    } catch (NumberFormatException e1) {
                        try {
                            val = (int) Math.round(Double.parseDouble(cell.replace(',', '.')));
                        } catch (NumberFormatException ignored) {
                            val = 0;
                        }
                    }
                }
            }
            neededMaterialsMap.put(mats[i], val);
        }
    }

    /** URL zum Bild der angegebenen Phase (1-basiert), falls vorhanden. */
    public Optional<URL> getPhaseImageUrl(int oneBasedPhase) {
        // 1) Classpath-Ordner versuchen
        if (imagesResourceBase != null) {
            String base = imagesResourceBase.endsWith("/") ? imagesResourceBase : imagesResourceBase + "/";
            String res = base + oneBasedPhase + ".png";
            URL url = getClass().getResource(res);
            if (url != null) return Optional.of(url);
        }
        // 2) Dateisystem-Ordner versuchen
        if (imagesDirPath != null) {
            try {
                Path p = imagesDirPath.resolve(oneBasedPhase + ".png");
                if (Files.exists(p)) return Optional.of(p.toUri().toURL());
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    /** URL zum Bild der aktuellen Phase, falls vorhanden. */
    public Optional<URL> getCurrentPhaseImageUrl() {
        if (constructionPhase <= 0) return Optional.empty();
        return getPhaseImageUrl(constructionPhase);
        }

    /**
     * Nach dem Laden aus einem Save kannst du die transienten Pfade neu setzen,
     * falls du im Dateisystem arbeitest.
     */
    public void rebindFilesystemPaths(Path newCsvPath, Path newImagesDirPath) {
        this.csvPath = newCsvPath;
        this.imagesDirPath = newImagesDirPath;
    }

    // --------- internals ---------

    private void resetMaterialMapsToZero() {
        neededMaterialsMap.clear();
        payedMaterialsMap.clear();
        for (Material m : Material.values()) {
            neededMaterialsMap.put(m, 0);
            payedMaterialsMap.put(m, 0);
        }
    }

    private String[] readCsvRow(int oneBasedRowIndex) {
        if (oneBasedRowIndex <= 0) return null;

        // 1) expliziter Dateipfad
        if (csvPath != null) {
            try (var br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                return readRowFromReader(br, oneBasedRowIndex);
            } catch (Exception ignored) { /* fallback */ }
        }
        // 2) Classpath-Ressource
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

    public String getDisplayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : name;
    }

    // TODO
    @Override
    public void addTimedPrestige(double prestigeMultiplier) {
        
    }
}

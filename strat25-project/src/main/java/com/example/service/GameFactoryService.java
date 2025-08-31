package com.example.service;

import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.model.*;

public class GameFactoryService {

    /**
     * Erstellt ein neues Game mit 3 Families und 3 Teams.
     *
     * @param gameName Name des Spiels
     * @return neues Game-Objekt
     */
    public static Game newGame(String gameName) {
        // === Families definieren ===
        Family familyRed = new Family("Orléans", SerializableColor.of255(200, 30, 30));   
        Family familyBlue = new Family("La Rochefoucauld", SerializableColor.of255(30, 80, 200));   
        Family familyGreen = new Family("Noailles", SerializableColor.of255(20, 150, 60));  

        List<Family> families = List.of(familyRed, familyBlue, familyGreen);

        // === Teams definieren ===
        List<Team> teams = new ArrayList<>();

        // Rot-Familie → rötliche Farbtöne
        teams.add(new Team("Team 1", 1, SerializableColor.of255(255, 80, 80), familyRed));  
        teams.add(new Team("Team 2", 2, SerializableColor.of255(200, 50, 50), familyRed));    
        teams.add(new Team("Team 3", 3, SerializableColor.of255(255, 140, 100), familyRed));  

        // Blau-Familie → bläuliche Farbtöne
        teams.add(new Team("Team 4", 4, SerializableColor.of255(80, 140, 255), familyBlue));   
        teams.add(new Team("Team 5", 5, SerializableColor.of255(50, 100, 200), familyBlue)); 
        teams.add(new Team("Team 6", 6, SerializableColor.of255(100, 200, 255), familyBlue)); 

        // Grün-Familie → grünliche Farbtöne
        teams.add(new Team("Team 7", 7, SerializableColor.of255(120, 255, 120), familyGreen));  
        teams.add(new Team("Team 8", 8, SerializableColor.of255(60, 180, 60), familyGreen));   
        teams.add(new Team("Team 9", 9, SerializableColor.of255(40, 120, 40), familyGreen)); 


        List<CategoryInterface> categorys = new ArrayList<>();
        categorys.add(new Category("Kategorie 1", teams));
        categorys.add(new Category("Kategorie 2", teams));
        categorys.add(new Category("Kategorie 3", teams));

        Path revCsv = classpathCsvToTemp("/com/example/csv/revolution.csv");
        Path verCsv = classpathCsvToTemp("/com/example/csv/versailles.csv");

        Map<Material, Double> materialWorths = new HashMap<>();
        materialWorths.put(Material.BAUMSTAEMME, 1.205);
        materialWorths.put(Material.STEIN, 1.205);
        materialWorths.put(Material.WEIZEN, 0.6025);
        materialWorths.put(Material.ERZ, 0.6025);
        materialWorths.put(Material.BRETTER, 0.3346);
        materialWorths.put(Material.STEINZIEGEL, 0.1115);
        materialWorths.put(Material.BROT, 0.7807);
        materialWorths.put(Material.METALL, 1.1154);
        materialWorths.put(Material.WAFFEN, 1.2269);
        materialWorths.put(Material.MILITAERISCHE_STAERKE, 0.3346);
        materialWorths.put(Material.HYMNEN, 0.0536);


        CategoryInterface revolution = new BuildCategory("Revolution", teams, revCsv, materialWorths);
        CategoryInterface versailles = new BuildCategory("Versailles", teams, verCsv, materialWorths);

        categorys.add(revolution);
        categorys.add(versailles);

        // Neues Game-Objekt erzeugen
        Game game = new Game(gameName, families, categorys);

        return game;
    }



    /**
     * Lädt eine CSV vom Classpath und gibt einen Path zurück.
     * - Wenn die Ressource als normale Datei vorliegt (Dev), wird direkt ein Path erzeugt.
     * - Wenn sie im JAR liegt, wird sie in eine Temp-Datei kopiert und dieser Path zurückgegeben.
     */
    private static Path classpathCsvToTemp(String resourcePath) {
        try {
            var url = GameFactoryService.class.getResource(resourcePath);
            if (url == null) {
                throw new IllegalStateException("CSV resource not found: " + resourcePath);
            }
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                // Direkt als Path nutzbar (Dev/target/classes)
                return Paths.get(url.toURI());
            }
            // z. B. "jar:" -> in Temp-Datei kopieren
            try (InputStream in = GameFactoryService.class.getResourceAsStream(resourcePath)) {
                if (in == null) throw new IllegalStateException("CSV resource stream is null: " + resourcePath);
                Path tmp = Files.createTempFile("buildcat_", ".csv");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                tmp.toFile().deleteOnExit();
                return tmp;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve CSV resource: " + resourcePath, e);
        }
    }

}

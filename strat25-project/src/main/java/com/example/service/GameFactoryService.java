package com.example.service;

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
        Family familyRed = new Family("Orléans", SerializableColor.fromHex("#20699e"));
        Family familyBlue = new Family("La Rochefoucauld", SerializableColor.fromHex("#797978"));
        Family familyGreen = new Family("Noailles", SerializableColor.fromHex("#a0482c"));

        List<Family> families = List.of(familyRed, familyBlue, familyGreen);

        // === Teams definieren ===
        List<Team> teams = new ArrayList<>();


        // Rot-Familie → rötliche Farbtöne
        teams.add(new Team("Team 1", 1, SerializableColor.fromHex("#952929"), familyRed));
        teams.add(new Team("Team 2", 2, SerializableColor.fromHex("#a0482c"), familyRed));
        teams.add(new Team("Team 3", 3, SerializableColor.fromHex("#965d28"), familyRed));

        // Blau-Familie → bläuliche Farbtöne
        teams.add(new Team("Team 4", 4, SerializableColor.fromHex("#28509c"), familyBlue));
        teams.add(new Team("Team 5", 5, SerializableColor.fromHex("#20699e"), familyBlue));
        teams.add(new Team("Team 6", 6, SerializableColor.fromHex("#2b839e"), familyBlue));

        // Grün-Familie → grünliche Farbtöne
        teams.add(new Team("Team 7", 7, SerializableColor.fromHex("#5f5f5f"), familyGreen));
        teams.add(new Team("Team 8", 8, SerializableColor.fromHex("#797978"), familyGreen));
        teams.add(new Team("Team 9", 9, SerializableColor.fromHex("#9f9fa0"), familyGreen));

        List<CategoryInterface> categorys = new ArrayList<>();
        categorys.add(new Category("Politischer Einfluss", teams, "/com/example/images/category/politik.png"));
        categorys.add(new Category("Militärische Stärke", teams, "/com/example/images/category/militaer.png"));
        categorys.add(new Category("Unruhe", teams, "/com/example/images/category/unruhe.png"));
        categorys.add(new Category("Rückhalt im Volk", teams, "/com/example/images/category/rueckhalt.png"));

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
        materialWorths.put(Material.ARBEITSKRAFT, 0.3346);
        materialWorths.put(Material.MILITAERISCHE_STAERKE, 0.0536);
        materialWorths.put(Material.HYMNEN, 0.00112);

        CategoryInterface revolution = new BuildCategory(
                "Revolution", "Aufbau der Revolution", teams, "/com/example/csv/revolution.csv", materialWorths, "/com/example/images/revolution", "/com/example/images/category/revolution.PNG");

        CategoryInterface versailles = new BuildCategory(
                "Versailles", "Bau von Versailles", teams, "/com/example/csv/versailles.csv", materialWorths, "/com/example/images/versailles", "/com/example/images/category/versailles.PNG");

        categorys.add(revolution);
        categorys.add(versailles);

        // Neues Game-Objekt erzeugen
        Game game = new Game(gameName, families, categorys);

        return game;
    }

}

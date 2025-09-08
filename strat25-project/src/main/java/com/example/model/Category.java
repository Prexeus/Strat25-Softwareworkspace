package com.example.model;

import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Category implements Serializable, CategoryInterface {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Map<Integer, Double> influenceMap = new ConcurrentHashMap<>();

    // Lookup von TeamId -> Team, um ohne TeamManager auszukommen
    private final Map<Integer, Team> teamById = new ConcurrentHashMap<>();

    // Kategorien-spezifischer Multiplikator
    private volatile double prestigeMultiplier = 1.0;

    // Optionales statisches Kategorienbild (Classpath/URL/Dateipfad)
    private final String imageUrlSpec;

    // Alt: kompatibler Konstruktor (ohne Bild)
    public Category(String name, Collection<Team> teams) {
        this(name, teams, null);
    }

    // Neu: mit Bildangabe
    public Category(String name, Collection<Team> teams, String imageUrlSpec) {
        this.name = name;
        this.imageUrlSpec = imageUrlSpec;

        if (teams != null) {
            for (Team team : teams) {
                if (team != null) {
                    influenceMap.put(team.getId(), 0.0);
                    teamById.put(team.getId(), team);
                }
            }
        }
    }

    // Falls später Teams dynamisch hinzukommen sollen:
    public void registerTeam(Team team) {
        if (team == null)
            return;
        teamById.putIfAbsent(team.getId(), team);
        influenceMap.putIfAbsent(team.getId(), 0.0);
    }

    @Override
    public void addInfluence(Team team, double influence) {
        if (team == null)
            return;
        // Sicherstellen, dass Team bekannt ist
        teamById.putIfAbsent(team.getId(), team);
        influenceMap.merge(team.getId(), influence, Double::sum);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<Integer, Double> getInfluenceMap() {
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

    // Bildauflösung (Classpath -> URL -> Dateipfad)
    @Override
    public Optional<URL> getImageUrl() {
        if (imageUrlSpec == null || imageUrlSpec.isBlank())
            return Optional.empty();

        // 1) Classpath-Ressource
        try {
            URL cp = getClass().getResource(imageUrlSpec);
            if (cp != null)
                return Optional.of(cp);
        } catch (Exception ignored) {
        }

        // 2) Als absolute URL interpretieren
        try {
            return Optional.of(new URL(imageUrlSpec));
        } catch (Exception ignored) {
        }

        // 3) Als lokaler Dateipfad interpretieren
        try {
            Path p = Path.of(imageUrlSpec);
            if (Files.exists(p))
                return Optional.of(p.toUri().toURL());
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    /**
     * Verteilt Prestige gemäß Kommentar:
     * - Anteilig insg. 20 Prestige für die Teams (nach Influence)
     * - Je +5 Prestige für die TOP 3 Teams
     * Skaliert mit (Game-Multiplikator) * (Category-Multiplikator).
     *
     * @param generalPrestigeMultiplier Multiplikator aus dem Game
     */
    @Override
    public void addTimedPrestige(double generalPrestigeMultiplier) {
        double multiplier = generalPrestigeMultiplier * this.prestigeMultiplier;
        if (multiplier <= 0)
            return;

        // Positive Einflüsse herausfiltern
        List<Map.Entry<Integer, Double>> positive = influenceMap.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0.0)
                .toList();

        // 1) Gesamtprestige anteilig verteilen (insg. 20) – nur auf Teams mit >0
        // Einfluss
        double totalPositive = positive.stream()
                .mapToDouble(Map.Entry::getValue)
                .sum();

        if (totalPositive > 0.0) {
            double pool = 20.0 * multiplier;
            for (Map.Entry<Integer, Double> e : positive) {
                Team team = teamById.get(e.getKey());
                if (team == null)
                    continue;
                double share = e.getValue() / totalPositive;
                team.addPrestige(share * pool);
            }
        }

        // 2) Top 3 Bonus – nur Teams mit >0 Einfluss
        positive.stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .forEach(e -> {
                    Team team = teamById.get(e.getKey());
                    if (team != null) {
                        team.addPrestige(5.0 * multiplier);
                    }
                });
    }

}

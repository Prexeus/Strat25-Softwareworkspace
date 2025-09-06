package com.example.model;

import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Category implements Serializable, CategoryInterface {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Map<Integer, Double> influenceMap = new ConcurrentHashMap<>();
    private volatile double prestigeMultiplier = 1.0;

    // NEU: optionales statisches Kategorienbild (Classpath/URL/Dateipfad)
    private final String imageUrlSpec;

    // Alt: kompatibler Konstruktor
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
                }
            }
        }
    }

    @Override
    public void addInfluence(Team team, double influence) {
        if (team == null) return;
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

    // NEU: Bildauflösung (Classpath -> URL -> Dateipfad)
    @Override
    public Optional<URL> getImageUrl() {
        if (imageUrlSpec == null || imageUrlSpec.isBlank()) return Optional.empty();

        // 1) Classpath-Ressource
        try {
            URL cp = getClass().getResource(imageUrlSpec);
            if (cp != null) return Optional.of(cp);
        } catch (Exception ignored) {}

        // 2) Als absolute URL interpretieren
        try {
            return Optional.of(new URL(imageUrlSpec));
        } catch (Exception ignored) {}

        // 3) Als lokaler Dateipfad interpretieren
        try {
            Path p = Path.of(imageUrlSpec);
            if (Files.exists(p)) return Optional.of(p.toUri().toURL());
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    // TODO
    @Override
    public void addTimedPrestige(double prestigeMultiplier) {
        
    }
}

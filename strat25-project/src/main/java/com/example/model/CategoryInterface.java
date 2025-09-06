package com.example.model;

import java.net.URL;
import java.util.Map;
import java.util.Optional;

public interface CategoryInterface {
    String getName();
    Map<Integer, Double> getInfluenceMap();
    void addInfluence(Team team, double influence);
    double getPrestigeMultiplier();
    void setPrestigeMultiplier(double prestigeMultiplier);
    Optional<URL> getImageUrl();
    void addTimedPrestige(double prestigeMultiplier);
}

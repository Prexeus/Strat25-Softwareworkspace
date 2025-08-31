package com.example.model;

import java.util.Map;

public interface CategoryInterface {
    String getName();
    Map<Integer, Double> getInfluenceMap();
    void addInfluence(Team team, double influence);
    double getPrestigeMultiplier();
    void setPrestigeMultiplier(double prestigeMultiplier);
}

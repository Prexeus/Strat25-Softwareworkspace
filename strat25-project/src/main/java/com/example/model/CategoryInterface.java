package com.example.model;

import java.util.HashMap;

public interface CategoryInterface {
    String getName();
    HashMap<Integer, Double> getInfluenceMap();
    void addInfluence(Team team, double influence);
    double getPrestigeMultiplier();
    void setPrestigeMultiplier(double prestigeMultiplier);
}

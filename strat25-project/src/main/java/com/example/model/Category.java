package com.example.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



public class Category implements Serializable, CategoryInterface {

    private static final long serialVersionUID = 1L;

    private String name;
    
    private Map<Integer, Double> influenceMap = new ConcurrentHashMap<>();

    private volatile double prestigeMultiplier = 1.0;

    public Category(String name, Collection<Team> teams) {
        this.name = name;
        for (Team team : teams) {
            influenceMap.put(team.getId(), 0.0);
        }
    }

    @Override
    public void addInfluence(Team team, double influence) {
        influenceMap.put(team.getId(), influenceMap.get(team.getId()) + influence);
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

}

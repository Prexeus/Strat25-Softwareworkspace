package com.example.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;



public class Category implements Serializable {

    private String name;
    
    private HashMap<Integer, Double> influenceMap = new HashMap<>();

    public Category(String name, Collection<Team> teams) {
        this.name = name;
        for (Team team : teams) {
            influenceMap.put(team.getId(), 0.0);
        }
    }

    public void addInfluence(Team team, double influence) {
        influenceMap.put(team.getId(), influenceMap.get(team.getId()) + influence);
    }

    public void setInfluence(Team team, double influence) {
        influenceMap.put(team.getId(), influence);
    }

    public String getName() {
        return name;
    }

    public HashMap<Integer, Double> getInfluenceMap() {
        return influenceMap;
    }

}

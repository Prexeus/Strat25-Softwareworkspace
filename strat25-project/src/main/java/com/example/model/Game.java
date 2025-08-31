package com.example.model;

import java.io.Serializable;
import java.util.List;

import com.example.repository.RepositoryItem;

public class Game implements RepositoryItem, Serializable {

    private String name;

    private List<Family> families;
    private List<Category> categories;

    private GameTime gameTime = new GameTime();

    private double prestigeMultiplier = 1.0;

    public Game(String name, List<Family> families, List<Category> categories) {
        this.name = name;
        this.families = families;
        this.categories = categories;
    }

    public void addTimedPrestige() {

    }

    public String getName() {
        return name;
    }

    public List<Family> getFamilies() {
        return families;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public GameTime getGameTime() {
        return gameTime;
    }

    public double getPrestigeMultiplier() {
        return prestigeMultiplier;
    }

    public void setPrestigeMultiplier(double prestigeMultiplier) {
        this.prestigeMultiplier = prestigeMultiplier;
    }

    public void addPrestigeMultiplier(double amount) {
        this.prestigeMultiplier += amount;
    }

}

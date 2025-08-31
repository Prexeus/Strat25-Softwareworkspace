package com.example.model;

import java.io.Serializable;

public class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private SerializableColor color;
    private Integer id;

    private Family family;

    private double prestige;

    public Team(String name, int id, SerializableColor color, Family family) {
        this.name = name;
        this.id = id;
        this.color = color;
        this.family = family;
        family.addTeam(this);
    }

    public String getName() {
        return name;
    }

    public SerializableColor getColor() {
        return color;
    }

    public Family getFamily() {
        return family;
    }

    public Integer getId() {
        return id;
    }

    public double getPrestige() {
        return prestige;
    }

    public void setPrestige(double prestige) {
        this.prestige = prestige;
    }

    public void addPrestige(double prestige) {
        this.prestige += prestige;
    }
}

package com.example.model;

import java.io.Serializable;

public class Team implements Serializable {

    private String name;

    private SerializableColor color;
    private Integer id;

    private Family family;

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
}

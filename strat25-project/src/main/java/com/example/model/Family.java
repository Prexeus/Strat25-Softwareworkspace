package com.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class Family implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private SerializableColor color;

    private List<Team> teams;

    public Family(String name, SerializableColor color) {
        this.name = name;
        this.color = color;
    }

    public void addTeam(Team team) {
        if (teams == null) {
            teams = new ArrayList<>(3);
        }
        teams.add(team);
    }

    public String getName() {
        return name;
    }

    public SerializableColor getColor() {
        return color;
    }

    public List<Team> getTeams() {
        return teams;
    }

}

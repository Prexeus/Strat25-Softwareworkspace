package com.example.model;

import java.io.Serializable;

public class GameTime implements Serializable{

    private static final long serialVersionUID = 1L;

    private volatile double scaledSeconds = 0.0;
    private volatile double gameSpeed = 1.0;

    public double getScaledSeconds() {
        return scaledSeconds;
    }

    public double getGameSpeed() {
        return gameSpeed;
    }

    public long getRoundedScaledSeconds() {
        return Math.round(scaledSeconds);
    }

    public void setScaledSeconds(double scaledSeconds) {
        this.scaledSeconds = scaledSeconds;
    }

    public void setGameSpeed(double gameSpeed) {
        this.gameSpeed = gameSpeed;
    }

}

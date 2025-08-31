package com.example.time;
import com.example.model.Game;
import com.example.service.GameService;

import java.util.function.Supplier;

/**
 * GameRuntimeService
 * ------------------
 * Orchestrates time-driven gameplay for the active session.
 *
 * Responsibilities:
 *  - Owns a GameClock and registers playtime-based events (autosave, prestige, ...).
 *  - Exposes lifecycle controls: start / pause / resume / stop / close.
 *  - Reads & writes time/speed via the Game's persisted GameTime.
 *
 * Usage:
 *  - Construct with a GameService (must expose getGame())
 *  - Call start() when entering in-game, pause()/resume() from UI, stop()/close() when leaving/exit.
 */
public class GameRuntimeService implements AutoCloseable {

    private final GameService gameService;
    private final GameClock clock;

    /**
     * Preferred constructor. The clock will always operate on the current Game
     * from the GameService (even if you load/switch saves later).
     */
    public GameRuntimeService(GameService gameService) {
        this(gameService, 2);
    }

    public GameRuntimeService(GameService gameService, int clockWorkerThreads) {
        this.gameService = gameService;

        // Supplier that always returns the current Game from the session
        Supplier<Game> gameSupplier = () -> getGame();

        // Clock pulls & updates Game.gameTime on each real-second tick
        this.clock = new GameClock(gameSupplier, clockWorkerThreads);

        registerTimedEvents();
    }

    /**
     * Central place to declare recurring mechanics.
     * Adjust or extend as your game grows.
     */
    private void registerTimedEvents() {
        // AUTOSAVE: every 10 minutes of active playtime
        clock.registerPeriodicByGameTime(
                "autosave",
                () -> {
                    try {
                        gameService.saveGame();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                10 * 60
        );

        // PRESTIGE: every 10 seconds of active playtime
        // Delegates to the domain (Game) method you prepared.
        clock.registerPeriodicByGameTime(
                "prestigeDistribution",
                () -> {
                    getGame().addTimedPrestige();
                },
                10
        );

        // PRESTIGE MULTIPLIER: every 10 minutes of active playtime
        clock.registerPeriodicByGameTime(
                "prestigeMultiplier",
                () -> {
                    getGame().setPrestigeMultiplier(getGame().getPrestigeMultiplier() * 1.05);
                },
                10 * 60
        );

        // EXAMPLES for more mechanics:
        // clock.registerPeriodicByGameTime("bonus", () -> bonuses.applyTo(gameService.getGame()), 30);
        // clock.registerPeriodicByGameTime("spawn", () -> spawner.spawnWave(gameService.getGame()), 90);
    }

    // ----------------- Lifecycle -----------------

    /** Starts the playtime clock and enables event firing. */
    public void start()  { clock.start(); }

    /** Freezes playtime and events (no events fire while paused). */
    public void pause()  { clock.pause(); }

    /** Resumes playtime and events. */
    public void resume() { clock.resume(); }

    /**
     * Stops the 1s ticker; events remain registered but dormant until start() is called again.
     * Elapsed time is NOT reset (it's stored in Game.gameTime).
     */
    public void stop()   { clock.stop(); }

    /** For app shutdown / permanent dispose. */
    @Override
    public void close()  { clock.close(); }

    // -------------- Queries / Controls ----------

    public long   getElapsedSeconds()   { return clock.getElapsedSeconds(); }
    public double getElapsedSecondsExact() { return clock.getElapsedSecondsExact(); }
    public String getElapsedFormatted() { return clock.getElapsedFormatted(); }

    public void   setGameSpeed(double speed) { clock.setGameSpeed(speed); }
    public double getGameSpeed()             { return clock.getGameSpeed(); }

    /** Expose the clock if you want to register/remove events dynamically (optional). */
    public GameClock getClock() { return clock; }

    public Game getGame(){
        return gameService.getGame();
    }
}
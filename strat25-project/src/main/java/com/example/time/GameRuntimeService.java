package com.example.time;

import com.example.model.Game;
import com.example.service.GameService;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * GameRuntimeService
 * ------------------
 * Orchestrates time-driven gameplay for the active session.
 * Uses a single-threaded Logic Executor as the only writer to game state.
 *
 * Responsibilities:
 *  - Owns a GameClock and registers playtime-based events (autosave, prestige, ...).
 *  - Exposes lifecycle controls: start / pause / resume / stop / close.
 *  - Reads & writes time/speed via the Game's persisted GameTime.
 *
 * Usage:
 *  - Construct with a GameService (must expose getGame()).
 *  - Call start() when entering in-game; pause()/resume() from UI; stop()/close() on leave/exit.
 */
public class GameRuntimeService implements AutoCloseable {

    private final GameService gameService;

    /** The ONLY thread that mutates game state. */
    private final ExecutorService logic = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-logic");
        t.setDaemon(true);
        return t;
    });

    /** Optional separate pool for heavy I/O tasks if ever needed. */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-io");
        t.setDaemon(true);
        return t;
    });

    private final GameClock clock;

    /**
     * Preferred constructor. The clock will always operate on the current Game
     * from the GameService (even if you load/switch saves later).
     */
    public GameRuntimeService(GameService gameService) {
        this(gameService, /*unused legacy param*/ 0);
    }

    public GameRuntimeService(GameService gameService, int ignored) {
        this.gameService = gameService;

        // Supplier that always returns the current Game from the session
        Supplier<Game> gameSupplier = this::getGame;

        // Clock posts all ticks & events onto the logic executor
        this.clock = new GameClock(gameSupplier, logic);

        registerTimedEvents();
    }

    /**
     * Central place to declare recurring mechanics.
     * All jobs run on the logic thread (serial, thread-safe).
     */
    private void registerTimedEvents() {
        // AUTOSAVE: every 10 minutes of active playtime
        clock.registerPeriodicByGameTime(
                "autosave",
                () -> {
                    try {
                        // Pause time & events while saving to keep a clean snapshot.
                        pause();
                        gameService.saveGame(); // runs on logic thread; blocks logic briefly
                        gameService.backupGame();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        resume();
                    }
                },
                10 * 60
        );

        // PRESTIGE: every 10 seconds of active playtime
        clock.registerPeriodicByGameTime(
                "prestigeDistribution",
                () -> getGame().addTimedPrestige(),
                10
        );

        // PRESTIGE MULTIPLIER: every 10 minutes of active playtime
        clock.registerPeriodicByGameTime(
                "prestigeMultiplier",
                () -> {
                    Game g = getGame();
                    g.setPrestigeMultiplier(g.getPrestigeMultiplier() * 1.05);
                },
                10 * 60
        );
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
    public void close()  {
        stop();
        clock.close();
        logic.shutdownNow();
        io.shutdownNow();
    }

    // -------------- Queries / Controls ----------

    public long   getElapsedSeconds()      { return clock.getElapsedSeconds(); }
    public double getElapsedSecondsExact() { return clock.getElapsedSecondsExact(); }
    public String getElapsedFormatted()    { return clock.getElapsedFormatted(); }

    public void   setGameSpeed(double speed) { clock.setGameSpeed(speed); }
    public double getGameSpeed()             { return clock.getGameSpeed(); }

    /** Expose the clock if you want to register/remove events dynamically (optional). */
    public GameClock getClock() { return clock; }

    public Game getGame() { return gameService.getGame(); }

    // -------------- Logic API for controllers/services --------

    /** Post a mutation to the single logic thread (fire-and-forget). */
    public void runOnLogic(Runnable r) { logic.execute(r); }

    /** Compute a value on the logic thread (blocking). Use sparingly in UI. */
    public <T> T callOnLogic(Callable<T> c) {
        try { return logic.submit(c).get(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}

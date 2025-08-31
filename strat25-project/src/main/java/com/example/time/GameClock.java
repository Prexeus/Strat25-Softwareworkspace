package com.example.time;

import com.example.model.Game;
import com.example.model.GameTime;
import javafx.application.Platform;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * GameClock
 * ----------------
 * Purpose:
 *  - Advances a Game's GameTime in "game seconds" (scaled by gameSpeed).
 *  - Schedules periodic events relative to *game time* (not wall clock).
 *  - Supports start / pause / resume / stop / close.
 *
 * Persistence:
 *  - This class is NOT serialized.
 *  - Persist only GameTime (scaledSeconds & gameSpeed) inside Game.
 *  - On load, recreate GameClock, re-register events, and start/resume.
 *
 * Threading:
 *  - A ScheduledExecutorService ticks every real second.
 *  - Each tick is posted to the provided logic Executor (single writer).
 *  - Events are executed on the logic thread as well (no overlap).
 *  - For JavaFX UI updates, use GameClock.fx(() -> {*update UI*}).
 */
public class GameClock implements AutoCloseable {

    private final Supplier<Game> gameSupplier;
    private final Executor logic; // single-threaded, provided by GameRuntimeService

    // 1-second real-time ticker
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(daemon("game-time-scheduler"));

    // State flags
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused  = new AtomicBoolean(false);

    private ScheduledFuture<?> tickHandle;

    // Playtime-based events (managed on logic thread)
    private final Map<String, GameTimedEvent> events = new HashMap<>();

    /** Creates a GameClock that posts ticks & events onto the given logic executor. */
    public GameClock(Supplier<Game> gameSupplier, Executor logic) {
        this.gameSupplier = gameSupplier;
        this.logic = logic;
    }

    /** Starts time advancing and event checks. Idempotent. */
    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        paused.set(false);

        tickHandle = scheduler.scheduleAtFixedRate(() -> {
            if (!running.get() || paused.get()) return;
            // Post the whole tick to the logic thread (single writer)
            logic.execute(this::tickOnceOnLogic);
        }, 1, 1, TimeUnit.SECONDS);
    }

    /** Freezes time and event firing (events won't trigger while paused). */
    public void pause()  { paused.set(true); }

    /** Unfreezes time and event firing. */
    public void resume() { paused.set(false); }

    /**
     * Stops the 1s ticker; events remain registered but dormant.
     * Elapsed time stays in GameTime (not reset).
     */
    public synchronized void stop() {
        running.set(false);
        paused.set(false);
        if (tickHandle != null) {
            tickHandle.cancel(false);
            tickHandle = null;
        }
    }

    /** Shuts down executors. Call once when disposing the runtime / on app exit. */
    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    // ---------- Tick (runs on logic thread) ----------------------------------

    private void tickOnceOnLogic() {
        Game g = gameSupplier.get();
        if (g == null) return;
        GameTime gt = g.getGameTime();
        if (gt == null) return;

        // 1) advance active playtime by gameSpeed (scaledSeconds += gameSpeed)
        double next = gt.getScaledSeconds() + gt.getGameSpeed();
        gt.setScaledSeconds(next);

        // 2) check due events against whole game-seconds
        long now = (long) Math.floor(next); // floor avoids early firing

        // iterate over a copy to avoid CME if someone unregisters concurrently on logic
        for (GameTimedEvent ev : events.values().toArray(new GameTimedEvent[0])) {
            if (now >= ev.nextDueSeconds) {
                try {
                    ev.job.run(); // runs on logic thread (serial)
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    long nextDue = now + ev.periodSeconds;
                    if (nextDue <= now) nextDue = now + 1; // safety net
                    ev.nextDueSeconds = nextDue;
                }
            }
        }
    }

    // ---------- Queries & Convenience (proxy to GameTime) ---------------------

    /** Returns whole game-seconds (floored). */
    public long getElapsedSeconds() {
        Game g = gameSupplier.get();
        if (g == null || g.getGameTime() == null) return 0L;
        return (long) Math.floor(g.getGameTime().getScaledSeconds());
    }

    /** Returns precise game-seconds (double). */
    public double getElapsedSecondsExact() {
        Game g = gameSupplier.get();
        if (g == null || g.getGameTime() == null) return 0.0;
        return g.getGameTime().getScaledSeconds();
    }

    /** Returns elapsed game time formatted as HH:MM:SS (floored). */
    public String getElapsedFormatted() {
        long s = getElapsedSeconds();
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** Sets the game speed factor (e.g., 1.0 = real-time, 2.0 = 2x). */
    public void setGameSpeed(double speed) {
        if (speed <= 0) throw new IllegalArgumentException("Speed must be > 0");
        Game g = gameSupplier.get();
        if (g != null && g.getGameTime() != null) {
            // This may be called from UI thread; write is simple and visible on next logic tick.
            g.getGameTime().setGameSpeed(speed);
        }
    }

    /** Returns the current game speed factor. */
    public double getGameSpeed() {
        Game g = gameSupplier.get();
        if (g == null || g.getGameTime() == null) return 1.0;
        return g.getGameTime().getGameSpeed();
    }

    /** Resets the counted playtime to 0. */
    public void resetTime() {
        Game g = gameSupplier.get();
        if (g != null && g.getGameTime() != null) {
            g.getGameTime().setScaledSeconds(0.0);
        }
    }

    // ---------- Event Registration -------------------------------------------

    /**
     * Registers an event that fires every 'periodSeconds' of *game time*.
     * First execution occurs after 'periodSeconds'.
     */
    public void registerPeriodicByGameTime(String name, Runnable job, long periodSeconds) {
        registerPeriodicByGameTime(name, job, periodSeconds, periodSeconds);
    }

    /**
     * Registers an event that fires every 'periodSeconds' of *game time*,
     * with a custom initial delay. If initialDelaySeconds==0, it fires immediately (on next eligible tick).
     */
    public synchronized void registerPeriodicByGameTime(String name, Runnable job,
                                                        long periodSeconds, long initialDelaySeconds) {
        if (periodSeconds <= 0) throw new IllegalArgumentException("periodSeconds must be > 0");
        if (initialDelaySeconds < 0) throw new IllegalArgumentException("initialDelaySeconds must be >= 0");
        if (events.containsKey(name)) throw new IllegalStateException("Event already exists: " + name);

        long startNow = getElapsedSeconds(); // current whole game-seconds
        events.put(name, new GameTimedEvent(name, job, periodSeconds, initialDelaySeconds, startNow));
    }

    /** Removes a registered event by name. Call on the logic thread for safety. */
    public synchronized void unregister(String name) { events.remove(name); }

    /** Removes all registered events. */
    public synchronized void clearEvents() { events.clear(); }

    /** Utility for JavaFX: safely run on FX Application Thread. */
    public static Runnable fx(Runnable uiWork) { return () -> Platform.runLater(uiWork); }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Container for a playtime-based periodic event. */
    private static final class GameTimedEvent {
        final String name;
        final Runnable job;          // executed on logic thread
        final long periodSeconds;
        final long initialDelaySeconds;
        volatile long nextDueSeconds; // next due time in whole *game seconds*

        GameTimedEvent(String name, Runnable job, long periodSeconds,
                       long initialDelaySeconds, long startNowSeconds) {
            this.name = name;
            this.job = job;
            this.periodSeconds = periodSeconds;
            this.initialDelaySeconds = initialDelaySeconds;
            this.nextDueSeconds = startNowSeconds + initialDelaySeconds;
        }
    }
}

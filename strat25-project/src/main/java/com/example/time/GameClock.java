package com.example.time;

import com.example.model.Game;
import com.example.model.GameTime;
import javafx.application.Platform;

import java.util.Map;
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
 *  - Prevents overlapping executions per event.
 *  - Uses a worker pool so event jobs (e.g., I/O) never block the 1s timer.
 *
 * Persistence:
 *  - This class is NOT serialized.
 *  - Persist only GameTime (scaledSeconds & gameSpeed) inside Game.
 *  - On load, recreate GameClock, re-register events, and start/resume.
 *
 * Threading:
 *  - A single ScheduledExecutorService ticks every real second.
 *  - A small fixed worker pool runs event jobs (prevents timer blockage).
 *  - For JavaFX UI updates, use GameClock.fx(() -> {* update UI *}).
 */
public class GameClock implements AutoCloseable {

    private final Supplier<Game> gameSupplier;

    // 1-second real-time ticker
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(daemon("game-time-scheduler"));

    // Worker pool for event jobs
    private final ExecutorService workers;

    // State flags
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused  = new AtomicBoolean(false);

    private ScheduledFuture<?> tickHandle;

    // Playtime-based events
    private final Map<String, GameTimedEvent> events = new ConcurrentHashMap<>();

    /** Creates a GameClock with 2 worker threads (good default). */
    public GameClock(Supplier<Game> gameSupplier) {
        this(gameSupplier, 2);
    }

    /**
     * @param gameSupplier   supplies the current Game (may return null if no game loaded)
     * @param workerThreads  number of worker threads for event jobs (>=1 recommended).
     *                       If <= 0, a DirectExecutor is used (jobs run on scheduler thread).
     */
    public GameClock(Supplier<Game> gameSupplier, int workerThreads) {
        this.gameSupplier = gameSupplier;
        if (workerThreads <= 0) {
            this.workers = new DirectExecutor();
        } else {
            this.workers = Executors.newFixedThreadPool(workerThreads, daemon("game-time-worker"));
        }
    }

    /** Starts time advancing and event checks. Idempotent. */
    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        paused.set(false);

        tickHandle = scheduler.scheduleAtFixedRate(() -> {
            if (!running.get() || paused.get()) return;

            Game g = gameSupplier.get();
            if (g == null) return;
            GameTime gt = g.getGameTime();
            if (gt == null) return;

            // 1) advance active playtime by gameSpeed (scaledSeconds += gameSpeed)
            double next = gt.getScaledSeconds() + gt.getGameSpeed();
            gt.setScaledSeconds(next);

            // 2) check due events against whole game-seconds
            long now = (long) Math.floor(next); // floor avoids early firing
            tickEvents(now);
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
        workers.shutdownNow();
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

    /** Removes a registered event by name. */
    public void unregister(String name) { events.remove(name); }

    /** Removes all registered events. */
    public void clearEvents() { events.clear(); }

    /** Utility for JavaFX: safely run on FX Application Thread. */
    public static Runnable fx(Runnable uiWork) { return () -> Platform.runLater(uiWork); }

    // ---------- internals -----------------------------------------------------

    private void tickEvents(long nowSeconds) {
        for (GameTimedEvent ev : events.values()) {
            if (nowSeconds >= ev.nextDueSeconds) {
                // Prevent overlap (skip if previous execution still running)
                if (!ev.runningJob.compareAndSet(false, true)) continue;

                workers.execute(() -> {
                    try {
                        ev.job.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        long next = nowSeconds + ev.periodSeconds;
                        if (next <= nowSeconds) next = nowSeconds + 1; // safety net
                        ev.nextDueSeconds = next;
                        ev.runningJob.set(false);
                    }
                });
            }
        }
    }

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
        final Runnable job;
        final long periodSeconds;
        final long initialDelaySeconds;
        volatile long nextDueSeconds; // next due time in whole *game seconds*
        final AtomicBoolean runningJob = new AtomicBoolean(false);

        GameTimedEvent(String name, Runnable job, long periodSeconds,
                       long initialDelaySeconds, long startNowSeconds) {
            this.name = name;
            this.job = job;
            this.periodSeconds = periodSeconds;
            this.initialDelaySeconds = initialDelaySeconds;
            this.nextDueSeconds = startNowSeconds + initialDelaySeconds;
        }
    }

    /** Direct executor fallback (avoid for I/O heavy jobs). */
    private static final class DirectExecutor implements ExecutorService {
        private volatile boolean shutdown;

        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public java.util.List<Runnable> shutdownNow() { shutdown = true; return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public <T> Future<T> submit(Callable<T> task) { return CompletableFuture.supplyAsync(() -> { try { return task.call(); } catch (Exception e) { throw new CompletionException(e); } }); }
        @Override public <T> Future<T> submit(Runnable task, T result) { task.run(); return CompletableFuture.completedFuture(result); }
        @Override public Future<?> submit(Runnable task) { task.run(); return CompletableFuture.completedFuture(null); }
        @Override public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }
}

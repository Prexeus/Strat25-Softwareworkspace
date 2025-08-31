package com.example.repository;

import java.io.Serializable;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generic file-based repository for persisting and restoring objects that
 * implement {@link RepositoryItem} and {@link Serializable}.
 *
 * <p><b>Features</b></p>
 * <ul>
 *   <li>Saves and loads main files under {@code data/repository/}</li>
 *   <li>Creates timestamped backups under {@code backups/<name>/}</li>
 *   <li>Configurable base directory and file extension</li>
 * </ul>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * RepositoryService<Game> repo = new RepositoryService<>();
 * repo.save(game);
 * Game loaded = repo.load("MySave");
 * repo.backup(game);
 * }</pre>
 *
 * @param <T> the item type; must extend {@link RepositoryItem} and be {@link Serializable}
 */
public class RepositoryService<T extends RepositoryItem & Serializable> {

    private final Path repoDir;
    private final String fileExtension;
    private final ObjectSerializer<T> io;

    private static final Path DEFAULT_REPO_DIR = Paths.get("data", "repository");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Creates a new RepositoryService that stores data under {@code data/repository/}
     * using {@code .ser} as file extension.
     */
    public RepositoryService() {
        this(DEFAULT_REPO_DIR);
    }

    /**
     * Creates a new RepositoryService for a custom base directory.
     *
     * @param repoDir the base directory where data should be stored
     */
    public RepositoryService(Path repoDir) {
        this(repoDir, ".ser", new ObjectSerializer<>());
    }

    /**
     * Creates a fully customized RepositoryService.
     *
     * @param repoDir       the base directory where data should be stored
     * @param fileExtension the file extension to use (e.g. ".ser")
     * @param io            the I/O handler responsible for serialization
     */
    public RepositoryService(Path repoDir, String fileExtension, ObjectSerializer<T> io) {
        this.repoDir = repoDir;
        this.fileExtension = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        this.io = io;
        ensureDir(this.repoDir);
    }

    /**
     * Saves (overwrites) the main file for the given item.
     *
     * @param item the object to save
     */
    public void save(T item) {
        Path target = primaryPathFor(item.getName());
        io.save(item, target.toString());
    }

    /**
     * Loads the main file for the given item name.
     *
     * @param name the item's name (used as filename)
     * @return the deserialized object
     * @throws Exception if the file cannot be loaded
     */
    public T load(final String name) throws Exception {
        Path source = primaryPathFor(name);
        return io.load(source.toString());
    }

    /**
     * Creates a timestamped backup copy of the given item.
     * Recommended to be called periodically (e.g., via ScheduledExecutorService).
     *
     * @param item the object to back up
     */
    public void backup(T item) {
        Path backup = backupPathFor(item.getName());
        ensureDir(backup.getParent());
        io.save(item, backup.toString());
    }

    // ----- internals -----

    private Path primaryPathFor(String name) {
        return repoDir.resolve(name + fileExtension);
    }

    private Path backupPathFor(String name) {
        // backups/<name>/<name>_yyyyMMdd_HHmmss.ser
        String ts = LocalDateTime.now().format(TS);
        return repoDir
                .resolve("backups")
                .resolve(name)
                .resolve(name + "_" + ts + fileExtension);
    }

    private static void ensureDir(Path dir) {
        try {
            if (dir != null) Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("Could not create directory: " + dir, e);
        }
    }
}

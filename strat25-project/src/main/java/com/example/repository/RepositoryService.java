package com.example.repository;

import java.io.Serializable;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generic file-based repository for persisting and restoring objects that
 * implement {@link RepositoryItem} and {@link Serializable}.
 *
 * <p><b>Layout</b></p>
 * <pre>
 * repoDir/                              (default: data/repository)
 *   <name>.ext                          (primary saves)
 *   backups/
 *     <name>/
 *       <name>_yyyyMMdd_HHmmss.ext      (timestamped backups)
 * </pre>
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

    // -------------------- CRUD --------------------

    /** Saves (overwrites) the main file for the given item. */
    public void save(T item) {
        String safe = safeName(item.getName());
        Path target = primaryPathFor(safe);
        io.save(item, target.toString());
    }

    /** Loads the main file for the given name. */
    public T load(final String name) throws Exception {
        String safe = safeName(name);
        Path source = primaryPathFor(safe);
        return io.load(source.toString());
    }

    /** Creates a timestamped backup copy of the given item. */
    public void backup(T item) {
        String safe = safeName(item.getName());
        Path backup = backupPathFor(safe);
        ensureDir(backup.getParent());
        io.save(item, backup.toString());
    }

    // -------------------- Listing --------------------

    /**
     * Lists all save names (without extension) located directly under {@code repoDir}.
     */
    public List<String> listSaves() {
        ensureDir(repoDir);
        try (Stream<Path> s = Files.list(repoDir)) {
            return s.filter(p -> Files.isRegularFile(p))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(fileExtension))
                    .map(n -> n.substring(0, n.length() - fileExtension.length()))
                    .sorted(String::compareToIgnoreCase)
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Lists all backup file names for the given base save (file names including extension),
     * sorted newest-first (lexicographically works with the timestamp pattern).
     */
    public List<String> listBackups(String baseName) {
        Path dir = backupDirFor(safeName(baseName));
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder()) // newest first (yyyyMMdd_HHmmss)
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // -------------------- Backups: load / restore --------------------

    /**
     * Loads a specific backup file for the given base save.
     * @param baseName the base save name (directory under backups/)
     * @param backupFileName the file name inside that directory (e.g. "save_20250101_120000.ser")
     */
    public T loadBackup(String baseName, String backupFileName) throws Exception {
        Path file = backupDirFor(safeName(baseName)).resolve(backupFileName);
        return io.load(file.toString());
    }

    /**
     * Loads the newest available backup for the given base save.
     * @throws java.util.NoSuchElementException if no backups exist
     */
    public T loadLatestBackup(String baseName) throws Exception {
        List<String> list = listBackups(baseName);
        if (list.isEmpty()) throw new java.util.NoSuchElementException("No backups for " + baseName);
        return loadBackup(baseName, list.get(0)); // newest-first sorting
    }

    /**
     * Restores a backup over the primary save file (copy & overwrite).
     * Does not return the object; caller may choose to call {@link #load(String)} afterwards.
     */
    public void restoreBackupToPrimary(String baseName, String backupFileName) {
        String safe = safeName(baseName);
        Path src = backupDirFor(safe).resolve(backupFileName);
        Path dst = primaryPathFor(safe);
        ensureDir(dst.getParent());
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore backup: " + src + " -> " + dst, e);
        }
    }

    // -------------------- Helpers --------------------

    /** True if a primary save file exists for the given name. */
    public boolean existsSave(String name) {
        return Files.exists(primaryPathFor(safeName(name)));
    }

    /** Returns the base repository directory. */
    public Path getRepoDir() {
        return repoDir;
    }

    /** Returns the file extension used for saves/backups (including leading dot). */
    public String getFileExtension() {
        return fileExtension;
    }

    // -------------------- internals --------------------

    private Path primaryPathFor(String safeName) {
        return repoDir.resolve(safeName + fileExtension);
    }

    private Path backupPathFor(String safeName) {
        // backups/<name>/<name>_yyyyMMdd_HHmmss.ext
        String ts = LocalDateTime.now().format(TS);
        return backupDirFor(safeName).resolve(safeName + "_" + ts + fileExtension);
    }

    private Path backupDirFor(String safeName) {
        return repoDir.resolve("backups").resolve(safeName);
    }

    private static void ensureDir(Path dir) {
        try {
            if (dir != null) Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("Could not create directory: " + dir, e);
        }
    }

    /**
     * Produces a filesystem-safe name (letters, digits, space, dot, dash, underscore).
     * Anything else becomes an underscore; trims and squashes repeats.
     * Keeps behavior stable across save/load/backup calls.
     */
    private static String safeName(String raw) {
        if (raw == null) return "unnamed";
        String s = raw.trim().replaceAll("[^A-Za-z0-9 ._\\-]", "_");
        s = s.replaceAll("_+", "_");
        if (s.isEmpty()) s = "unnamed";
        return s;
    }
}

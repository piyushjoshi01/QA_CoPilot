package com.qacopilot.generation.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Persists and retrieves {@link ContractSnapshot}s and {@link ContractDelta}s on disk.
 *
 * <p>Directory layout under {@code .qacopilot/evolution/}:
 * <pre>
 *   snapshot-v1.json
 *   snapshot-v2.json
 *   snapshot-v3.json          ← latest
 *   delta-v1-to-v2.json
 *   delta-v2-to-v3.json       ← latest delta
 * </pre>
 * </p>
 */
public final class SnapshotStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("snapshot-v(\\d+)\\.json");
    private static final Pattern DELTA_PATTERN = Pattern.compile("delta-v(\\d+)-to-v(\\d+)\\.json");

    private final Path evolutionDir;

    public SnapshotStore(Path qacopilotDir) {
        this.evolutionDir = qacopilotDir.resolve("evolution");
    }

    // ── Snapshots ─────────────────────────────────────────────────────────────

    /** Load the most recent snapshot, or empty if none exist. */
    public Optional<ContractSnapshot> loadLatestSnapshot() throws IOException {
        return findLatestSnapshotFile().map(file -> {
            try {
                return MAPPER.readValue(file.toFile(), ContractSnapshot.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read snapshot: " + file, e);
            }
        });
    }

    /** Save a new snapshot and return the version number used. */
    public int saveSnapshot(ContractSnapshot snapshot) throws IOException {
        Files.createDirectories(evolutionDir);
        Path file = evolutionDir.resolve("snapshot-v" + snapshot.version + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), snapshot);
        return snapshot.version;
    }

    // ── Deltas ────────────────────────────────────────────────────────────────

    /** Save a delta between two snapshot versions. */
    public void saveDelta(ContractDelta delta) throws IOException {
        Files.createDirectories(evolutionDir);
        Path file = evolutionDir.resolve(
                "delta-v" + delta.fromVersion + "-to-v" + delta.toVersion + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), delta);
    }

    /** Load the most recent delta, or empty if none exist. */
    public Optional<ContractDelta> loadLatestDelta() throws IOException {
        return findLatestDeltaFile().map(file -> {
            try {
                return MAPPER.readValue(file.toFile(), ContractDelta.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read delta: " + file, e);
            }
        });
    }

    /** Returns the next available snapshot version number (1 if no snapshots exist). */
    public int nextVersion() throws IOException {
        return latestSnapshotVersion() + 1;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Optional<Path> findLatestSnapshotFile() throws IOException {
        if (!Files.isDirectory(evolutionDir)) return Optional.empty();
        try (Stream<Path> files = Files.list(evolutionDir)) {
            return files.filter(p -> SNAPSHOT_PATTERN.matcher(p.getFileName().toString()).matches())
                    .max(Comparator.comparingInt(this::snapshotVersion));
        }
    }

    private Optional<Path> findLatestDeltaFile() throws IOException {
        if (!Files.isDirectory(evolutionDir)) return Optional.empty();
        try (Stream<Path> files = Files.list(evolutionDir)) {
            return files.filter(p -> DELTA_PATTERN.matcher(p.getFileName().toString()).matches())
                    .max(Comparator.comparingInt(this::deltaToVersion));
        }
    }

    private int snapshotVersion(Path file) {
        Matcher m = SNAPSHOT_PATTERN.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private int deltaToVersion(Path file) {
        Matcher m = DELTA_PATTERN.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    }

    private int latestSnapshotVersion() throws IOException {
        return findLatestSnapshotFile()
                .map(this::snapshotVersion)
                .orElse(0);
    }

    /** Returns the directory where evolution files are stored. */
    public Path evolutionDir() {
        return evolutionDir;
    }
}

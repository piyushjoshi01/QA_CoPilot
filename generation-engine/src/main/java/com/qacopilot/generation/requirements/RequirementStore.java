package com.qacopilot.generation.requirements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists and loads structured requirements under {@code .qacopilot/requirements/}.
 *
 * <p>Storage format ({@code .qacopilot/requirements/requirements.json}):
 * <pre>{@code
 * {
 *   "version": 1,
 *   "lastUpdated": "2026-04-25T10:00:00Z",
 *   "sourceFile": "requirements.pdf",
 *   "requirements": [ { ... } ]
 * }
 * }</pre>
 * </p>
 */
public final class RequirementStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE_NAME = "requirements.json";

    private final Path storeFile;

    public RequirementStore(Path qacopilotDir) {
        this.storeFile = qacopilotDir.resolve("requirements").resolve(FILE_NAME);
    }

    /** Load persisted requirements. Returns an empty store if none are saved yet. */
    public StoredRequirements loadOrEmpty() throws IOException {
        if (!Files.isRegularFile(storeFile)) {
            return new StoredRequirements();
        }
        StoredRequirements stored = MAPPER.readValue(storeFile.toFile(), StoredRequirements.class);
        if (stored.requirements == null) stored.requirements = new ArrayList<>();
        return stored;
    }

    /** Persist requirements extracted from the given source file. */
    public void save(String sourceFile, List<Requirement> requirements) throws IOException {
        Files.createDirectories(storeFile.getParent());
        StoredRequirements stored = new StoredRequirements();
        stored.sourceFile = sourceFile;
        stored.lastUpdated = Instant.now().toString();
        stored.requirements = new ArrayList<>(requirements);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(storeFile.toFile(), stored);
    }

    /** Load + merge new requirements (adding or updating by requirementId). */
    public void merge(String sourceFile, List<Requirement> newRequirements) throws IOException {
        StoredRequirements existing = loadOrEmpty();
        Map<String, Requirement> byId = existing.requirements.stream()
                .collect(Collectors.toMap(r -> r.requirementId, Function.identity()));
        for (Requirement r : newRequirements) {
            byId.put(r.requirementId, r);
        }
        existing.requirements = new ArrayList<>(byId.values());
        existing.lastUpdated = Instant.now().toString();
        if (sourceFile != null) existing.sourceFile = sourceFile;
        Files.createDirectories(storeFile.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(storeFile.toFile(), existing);
    }

    /** Returns the path to the requirements JSON file. */
    public Path storeFilePath() {
        return storeFile;
    }

    // ── Storage DTO ───────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StoredRequirements {
        public int version = 1;
        public String lastUpdated;
        public String sourceFile;
        public List<Requirement> requirements = new ArrayList<>();
    }
}

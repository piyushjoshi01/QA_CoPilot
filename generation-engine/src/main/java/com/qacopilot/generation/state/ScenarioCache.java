package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qacopilot.generation.scenario.ScenarioCacheEntry;
import com.qacopilot.generation.scenario.ScenarioCacheRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Persists generated Gherkin fragments per endpoint under {@code .qacopilot/scenario-cache.json}.
 */
public final class ScenarioCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final ScenarioCacheRoot root;

    private ScenarioCache(Path file, ScenarioCacheRoot root) {
        this.file = file;
        this.root = root;
    }

    public static ScenarioCache loadOrEmpty(Path qacopilotDir) throws IOException {
        Files.createDirectories(qacopilotDir);
        Path f = qacopilotDir.resolve("scenario-cache.json");
        if (!Files.isRegularFile(f)) {
            return new ScenarioCache(f, new ScenarioCacheRoot());
        }
        ScenarioCacheRoot parsed = MAPPER.readValue(f.toFile(), ScenarioCacheRoot.class);
        if (parsed.version != 1) {
            return new ScenarioCache(f, new ScenarioCacheRoot());
        }
        if (parsed.byEndpointId == null) {
            parsed.byEndpointId = new java.util.LinkedHashMap<>();
        }
        return new ScenarioCache(f, parsed);
    }

    public String getGherkinIfFresh(String stableId, String currentSignature, String generatorId) {
        ScenarioCacheEntry e = root.byEndpointId.get(stableId);
        if (e == null) {
            return null;
        }
        if (!Objects.equals(e.signature, currentSignature)) {
            return null;
        }
        if (!Objects.equals(e.generatorId, generatorId)) {
            return null;
        }
        return e.gherkinBlock;
    }

    public void put(String stableId, String signature, String generatorId, String gherkinBlock) {
        root.byEndpointId.put(stableId, new ScenarioCacheEntry(signature, generatorId, gherkinBlock));
    }

    public void retainOnly(Set<String> stableIds) {
        root.byEndpointId.keySet().retainAll(stableIds);
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }
}

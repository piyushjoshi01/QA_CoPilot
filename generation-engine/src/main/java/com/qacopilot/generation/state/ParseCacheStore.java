package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.ir.EndpointContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-file SHA-256 + serialized endpoints under {@code .qacopilot/parse-cache.json}. Invalidated when
 * {@code analysisFingerprint} changes.
 */
public final class ParseCacheStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final int SCHEMA = 1;

    private final String analysisFingerprint;
    private final Map<String, CachedFile> files;

    public ParseCacheStore(String analysisFingerprint, Map<String, CachedFile> files) {
        this.analysisFingerprint = analysisFingerprint;
        this.files = new LinkedHashMap<>(files);
    }

    public static ParseCacheStore loadOrEmpty(Path cacheFile, String analysisFingerprint) throws IOException {
        if (!Files.isRegularFile(cacheFile)) {
            return new ParseCacheStore(analysisFingerprint, Map.of());
        }
        ObjectNode root = (ObjectNode) MAPPER.readTree(cacheFile.toFile());
        if (root.path("schemaVersion").asInt(0) != SCHEMA) {
            return new ParseCacheStore(analysisFingerprint, Map.of());
        }
        if (!analysisFingerprint.equals(root.path("analysisFingerprint").asText(""))) {
            return new ParseCacheStore(analysisFingerprint, Map.of());
        }
        Map<String, CachedFile> map = new LinkedHashMap<>();
        JsonNode filesNode = root.get("files");
        if (filesNode != null && filesNode.isObject()) {
            ObjectNode fo = (ObjectNode) filesNode;
            for (java.util.Iterator<String> it = fo.fieldNames(); it.hasNext(); ) {
                String rel = it.next();
                JsonNode entry = fo.get(rel);
                if (!entry.isObject()) {
                    continue;
                }
                ObjectNode en = (ObjectNode) entry;
                String hash = en.path("sha256").asText("");
                ArrayNode eps = (ArrayNode) en.get("endpoints");
                List<EndpointContract> list = new ArrayList<>();
                if (eps != null) {
                    for (JsonNode n : eps) {
                        if (n.isObject()) {
                            list.add(EndpointContractSerde.fromObjectNode((ObjectNode) n));
                        }
                    }
                }
                map.put(rel, new CachedFile(hash, list));
            }
        }
        return new ParseCacheStore(analysisFingerprint, map);
    }

    public void put(String relativePath, String sha256, List<EndpointContract> endpoints) {
        List<EndpointContract> copy = List.copyOf(endpoints);
        files.put(relativePath, new CachedFile(sha256, copy));
    }

    public CachedFile get(String relativePath) {
        return files.get(relativePath);
    }

    public void retainOnly(java.util.Set<String> relativePaths) {
        files.keySet().retainAll(relativePaths);
    }

    public void save(Path cacheFile) throws IOException {
        Files.createDirectories(cacheFile.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA);
        root.put("analysisFingerprint", analysisFingerprint);
        ObjectNode outFiles = root.putObject("files");
        for (Map.Entry<String, CachedFile> e : files.entrySet()) {
            ObjectNode entry = outFiles.putObject(e.getKey());
            entry.put("sha256", e.getValue().sha256());
            ArrayNode arr = entry.putArray("endpoints");
            for (EndpointContract ep : e.getValue().endpoints()) {
                arr.add(EndpointContractSerde.toObjectNode(ep));
            }
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), root);
    }

    public record CachedFile(String sha256, List<EndpointContract> endpoints) {}
}

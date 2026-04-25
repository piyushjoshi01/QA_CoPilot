package com.qacopilot.generation.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIProvider;
import com.qacopilot.generation.ai.AIResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Disk-backed cache layer for AI responses.
 *
 * <p>Cache key: {@code SHA-256(provider:model|systemPrompt|userContent)}.
 * One JSON file per cache entry under {@code .qacopilot/ai-response-cache/}.
 *
 * <p>Entries are <em>never</em> automatically expired. Use {@code --scenario-mode regenerate-all}
 * to force fresh AI calls, or delete the cache directory manually.
 *
 * <p>Why cache at the AI level (not at the Gherkin level)?
 * The Gherkin-level {@link com.qacopilot.generation.state.ScenarioCache} caches per endpoint
 * signature. The AI response cache operates one level lower — it avoids repeat HTTP calls for
 * identical prompt payloads even when the endpoint signature changes but the prompt text happens
 * to be identical. Both caches are useful in complementary scenarios.
 *
 * <p>Observability: cache hits and stores are logged at FINE level; misses at FINEST.
 * Enable with {@code java.util.logging} or route to SLF4J if needed.</p>
 */
public final class AIResponseCache implements AIClient {

    private static final Logger LOG = Logger.getLogger(AIResponseCache.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AIClient delegate;
    private final Path cacheDir;

    public AIResponseCache(AIClient delegate, Path cacheDir) {
        this.delegate = delegate;
        this.cacheDir = cacheDir;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public AIProvider provider() {
        return delegate.provider();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        String key = cacheKey(systemPrompt, userContent);
        Path file = cacheDir.resolve("ai-cache-" + key + ".json");

        // ── Cache hit ─────────────────────────────────────────────────────────
        if (Files.isRegularFile(file)) {
            try {
                ObjectNode node = (ObjectNode) MAPPER.readTree(file.toFile());
                String content = node.path("content").asText();
                if (!content.isBlank()) {
                    LOG.fine(() -> "[AIResponseCache] HIT  " + key.substring(0, 12) + " → " + delegate.id());
                    return new AIResponse(
                            content,
                            delegate.id() + " (cached)",
                            node.path("promptTokens").asInt(0),
                            node.path("completionTokens").asInt(0),
                            Map.of("cached", true, "cachedAt", node.path("cachedAt").asText()));
                }
            } catch (IOException e) {
                LOG.warning("[AIResponseCache] Failed to read cache file " + file + ": " + e.getMessage());
            }
        }

        // ── Cache miss → delegate → persist ───────────────────────────────────
        AIResponse response = delegate.generateResponse(systemPrompt, userContent);

        try {
            Files.createDirectories(cacheDir);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("content", response.content());
            node.put("model", response.modelUsed());
            node.put("promptTokens", response.promptTokens());
            node.put("completionTokens", response.completionTokens());
            node.put("cachedAt", Instant.now().toString());
            node.put("provider", delegate.provider().getId());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), node);
            LOG.fine(() -> "[AIResponseCache] STORE " + key.substring(0, 12) + " → " + file.getFileName());
        } catch (IOException e) {
            LOG.warning("[AIResponseCache] Failed to write cache file: " + e.getMessage());
        }

        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cacheKey(String systemPrompt, String userContent) {
        String raw = delegate.provider().getId() + ":" + delegate.id() + "|" + systemPrompt + "|" + userContent;
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available on this JVM", e);
        }
    }
}

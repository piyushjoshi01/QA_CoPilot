package com.qacopilot.generation.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientConfig;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIProvider;
import com.qacopilot.generation.ai.AIResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Anthropic Messages API client (api.anthropic.com/v1/messages).
 *
 * <p>Supports Claude 3 / Claude 3.5 models. The Anthropic API uses a different
 * request/response format than OpenAI:
 * <ul>
 *   <li>Auth: {@code x-api-key} header instead of {@code Authorization: Bearer ...}</li>
 *   <li>System prompt: top-level {@code "system"} field, not inside {@code messages[]}</li>
 *   <li>Response: {@code content[0].text} (not {@code choices[0].message.content})</li>
 *   <li>Token counts: {@code usage.input_tokens} / {@code usage.output_tokens}</li>
 * </ul>
 * </p>
 *
 * <p>Required config: {@code apiKey} (ANTHROPIC_API_KEY / AI_API_KEY).</p>
 */
public final class AnthropicClient implements AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AIClientConfig config;
    private final HttpClient http;

    public AnthropicClient(AIClientConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 30)))
                .build();
    }

    @Override
    public String id() {
        return "anthropic:" + config.model();
    }

    @Override
    public AIProvider provider() {
        return AIProvider.ANTHROPIC;
    }

    @Override
    public boolean isAvailable() {
        return config.hasApiKey();
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        if (!config.hasApiKey()) {
            throw new AIClientException(
                    "Anthropic API key is not configured. Set ANTHROPIC_API_KEY or AI_API_KEY.", 401);
        }

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", config.model());
        payload.put("max_tokens", config.maxTokens());
        payload.put("system", systemPrompt);
        payload.set("messages", MAPPER.createArrayNode()
                .add(MAPPER.createObjectNode()
                        .put("role", "user")
                        .put("content", userContent)));

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(MESSAGES_URL))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json");
            config.extraHeaders().forEach(rb::header);

            HttpRequest req = rb.POST(
                    HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new AIClientException(
                        "Anthropic returned HTTP " + res.statusCode() + ": " + truncate(res.body(), 400),
                        res.statusCode());
            }

            JsonNode root = MAPPER.readTree(res.body());
            // Anthropic response: {"content":[{"type":"text","text":"..."}], "usage":{"input_tokens":N,"output_tokens":M}}
            String content = root.path("content").path(0).path("text").asText();
            if (content.isEmpty()) {
                throw new AIClientException("Anthropic returned empty content: " + truncate(res.body(), 400));
            }

            int promptTokens = root.path("usage").path("input_tokens").asInt(0);
            int completionTokens = root.path("usage").path("output_tokens").asInt(0);
            return new AIResponse(content, config.model(), promptTokens, completionTokens, Map.of());

        } catch (AIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AIClientException("Anthropic request failed: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

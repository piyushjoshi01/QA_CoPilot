package com.qacopilot.generation.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * OpenAI Chat Completions API client (api.openai.com/v1/chat/completions).
 *
 * <p>Supports {@code response_format: {type: "json_object"}} to enforce structured JSON output
 * and any custom {@code baseUrl} override for proxies or private deployments.</p>
 *
 * <p>Required config: {@code apiKey} (OPENAI_API_KEY / AI_API_KEY).</p>
 */
public final class OpenAIClient implements AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE = "https://api.openai.com/v1";

    private final AIClientConfig config;
    private final HttpClient http;
    private final URI chatUri;

    public OpenAIClient(AIClientConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 30)))
                .build();
        String base = config.baseUrl().isBlank() ? DEFAULT_BASE : stripTrailingSlash(config.baseUrl());
        this.chatUri = URI.create(base + "/chat/completions");
    }

    @Override
    public String id() {
        return "openai:" + config.model();
    }

    @Override
    public AIProvider provider() {
        return AIProvider.OPENAI;
    }

    @Override
    public boolean isAvailable() {
        return config.hasApiKey();
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        if (!config.hasApiKey()) {
            throw new AIClientException(
                    "OpenAI API key is not configured. Set OPENAI_API_KEY or AI_API_KEY env var.", 401);
        }

        ObjectNode payload = buildPayload(systemPrompt, userContent);

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(chatUri)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json");
            config.extraHeaders().forEach(rb::header);

            HttpRequest req = rb.POST(
                    HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new AIClientException(
                        "OpenAI returned HTTP " + res.statusCode() + ": " + truncate(res.body(), 400),
                        res.statusCode());
            }

            JsonNode root = MAPPER.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isEmpty()) {
                throw new AIClientException("OpenAI returned empty content. Full body: " + truncate(res.body(), 400));
            }

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new AIResponse(content, config.model(), promptTokens, completionTokens, Map.of());

        } catch (AIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AIClientException("OpenAI request failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildPayload(String systemPrompt, String userContent) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", config.model());
        payload.put("temperature", config.temperature());
        payload.put("max_tokens", config.maxTokens());
        payload.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));

        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userContent));
        payload.set("messages", messages);
        return payload;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

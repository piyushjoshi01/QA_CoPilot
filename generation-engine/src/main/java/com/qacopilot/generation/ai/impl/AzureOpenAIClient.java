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
 * Azure OpenAI Service client.
 *
 * <p>Key differences from vanilla OpenAI:
 * <ul>
 *   <li>The deployment name replaces the model name and is encoded in the base URL.</li>
 *   <li>Auth uses the {@code api-key} header instead of {@code Authorization: Bearer}.</li>
 *   <li>The URL pattern is: {@code {baseUrl}/chat/completions?api-version={apiVersion}}</li>
 * </ul>
 * </p>
 *
 * <p>Required config:
 * <pre>
 *   AI_PROVIDER=azure-openai
 *   AI_API_KEY=&lt;azure-resource-key&gt;
 *   AI_BASE_URL=https://&lt;resource&gt;.openai.azure.com/openai/deployments/&lt;deployment&gt;
 *   AI_API_VERSION=2024-02-01   # or newer
 * </pre>
 * </p>
 */
public final class AzureOpenAIClient implements AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_API_VERSION = "2024-02-01";

    private final AIClientConfig config;
    private final HttpClient http;
    private final URI chatUri;

    public AzureOpenAIClient(AIClientConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 30)))
                .build();
        String version = config.apiVersion().isBlank() ? DEFAULT_API_VERSION : config.apiVersion();
        String base = stripTrailingSlash(config.baseUrl());
        this.chatUri = URI.create(base + "/chat/completions?api-version=" + version);
    }

    @Override
    public String id() {
        return "azure-openai:" + config.model();
    }

    @Override
    public AIProvider provider() {
        return AIProvider.AZURE_OPENAI;
    }

    @Override
    public boolean isAvailable() {
        return config.hasApiKey() && !config.baseUrl().isBlank();
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        if (!config.hasApiKey()) {
            throw new AIClientException("Azure OpenAI API key is not configured (set AI_API_KEY).", 401);
        }
        if (config.baseUrl().isBlank()) {
            throw new AIClientException(
                    "Azure OpenAI base URL is not configured. Set AI_BASE_URL to " +
                    "https://<resource>.openai.azure.com/openai/deployments/<deployment>");
        }

        ObjectNode payload = buildPayload(systemPrompt, userContent);

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(chatUri)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("api-key", config.apiKey())    // Azure uses api-key, not Bearer
                    .header("Content-Type", "application/json");
            config.extraHeaders().forEach(rb::header);

            HttpRequest req = rb.POST(
                    HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new AIClientException(
                        "Azure OpenAI returned HTTP " + res.statusCode() + ": " + truncate(res.body(), 400),
                        res.statusCode());
            }

            JsonNode root = MAPPER.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isEmpty()) {
                throw new AIClientException("Azure OpenAI returned empty content: " + truncate(res.body(), 400));
            }

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new AIResponse(content, config.model(), promptTokens, completionTokens, Map.of());

        } catch (AIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AIClientException("Azure OpenAI request failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildPayload(String systemPrompt, String userContent) {
        ObjectNode payload = MAPPER.createObjectNode();
        // Azure ignores the "model" field (deployment is in the URL) but include for logging
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

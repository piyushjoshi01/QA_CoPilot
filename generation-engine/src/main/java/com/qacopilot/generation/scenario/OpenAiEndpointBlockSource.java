package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.openai.OpenAiConfig;
import com.qacopilot.generation.openai.OpenAiScenarioClient;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiEndpointBlockSource implements EndpointBlockSource {

    private static final int CHUNK = 8;

    private final OpenAiConfig config;
    private final TemplateEndpointBlockSource fallback = new TemplateEndpointBlockSource();

    public OpenAiEndpointBlockSource(OpenAiConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "ai";
    }

    @Override
    public Map<String, String> blocksFor(List<EndpointContract> endpoints) throws Exception {
        if (!config.hasApiKey()) {
            throw new IllegalStateException(
                    "Generator 'ai' needs an API key: --openai-api-key, OPENAI_API_KEY, "
                            + "qacopilot.openai.apiKey, .env / QA_COPILOT_DOTENV, or OpenAiConfig.LOCAL_TEST_OPENAI_API_KEY. "
                            + "If you edited LOCAL_TEST_OPENAI_API_KEY in source, rebuild the fat JAR: "
                            + "gradle :generation-engine:shadowJar"
            );
        }
        OpenAiScenarioClient client = new OpenAiScenarioClient(config);
        Map<String, String> all = new LinkedHashMap<>();
        List<EndpointContract> batch = new ArrayList<>();
        for (EndpointContract e : endpoints) {
            batch.add(e);
            if (batch.size() >= CHUNK) {
                flush(client, batch, all);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flush(client, batch, all);
        }
        return all;
    }

    private void flush(OpenAiScenarioClient client, List<EndpointContract> chunk, Map<String, String> sink) throws Exception {
        try {
            Map<String, String> got = client.generateBlocks(chunk);
            sink.putAll(got);
        } catch (Exception ex) {
            if (config.fallbackToTemplateOnError()) {
                System.err.println("QA CoPilot: OpenAI chunk failed (" + ex.getMessage() + "); using template fallback.");
                sink.putAll(fallback.blocksFor(chunk));
            } else {
                throw ex;
            }
        }
        List<EndpointContract> missing = new ArrayList<>();
        for (EndpointContract e : chunk) {
            String sid = EndpointFingerprints.stableId(e);
            String block = sink.get(sid);
            if (block == null || block.isBlank()) {
                missing.add(e);
            }
        }
        if (!missing.isEmpty()) {
            System.err.println(
                    "QA CoPilot: OpenAI returned no Gherkin for " + missing.size()
                            + " endpoint(s) in this chunk (often output truncation); using template for those."
            );
            sink.putAll(fallback.blocksFor(missing));
        }
        try {
            Thread.sleep(350);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

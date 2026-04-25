package com.qacopilot.generation.openai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiScenarioClientTest {

    @Test
    void parsesBlocksJson() throws Exception {
        String json =
                """
                        {"blocks":[{"stableId":"abc123","gherkin":"  @e_abc123\\n  Scenario: x\\n    Given API base URL is configured\\n"}]}
                        """;
        Map<String, String> m = OpenAiScenarioClient.parseBlocksJson(json);
        assertEquals(1, m.size());
        assertTrue(m.get("abc123").contains("Scenario:"));
    }
}

package com.qacopilot.generation.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGherkinSanitizerTest {

    @Test
    void insertsScenarioBetweenTagAndGiven() {
        String raw =
                """
                        Feature: X

                        @e_abc123
                        Given API base URL is configured
                        When I send a GET request to "/x"
                        Then response status should be 200
                        """
                        .stripIndent();
        String out = AiGherkinSanitizer.sanitize(raw);
        assertTrue(out.contains("Scenario:"), out);
        assertTrue(out.contains("    Given API base URL"), out);
    }

    @Test
    void fixesDocStringBlock() {
        String raw =
                """
                        Feature: X
                        @e_1
                        Given x
                        And request body is:
                        """
                        + "\"\"\"\n"
                        + "{}\n"
                        + "\"\"\"\n"
                        + "When I send a POST request to \"/a\"\n"
                        + "Then response status should be 200\n";
        raw = raw.stripIndent();
        String out = AiGherkinSanitizer.sanitize(raw);
        assertTrue(out.contains("      \"\"\""), out);
        assertTrue(out.contains("    When I send a POST"), out);
    }
}

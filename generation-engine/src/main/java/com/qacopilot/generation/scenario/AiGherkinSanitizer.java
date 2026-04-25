package com.qacopilot.generation.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes LLM output so Cucumber can parse it: inserts missing {@code Scenario:} lines and fixes
 * common indentation (steps and doc strings under scenarios).
 */
public final class AiGherkinSanitizer {

    private AiGherkinSanitizer() {
    }

    public static String sanitize(String gherkin) {
        if (gherkin == null || gherkin.isBlank()) {
            return gherkin;
        }
        List<String> lines = new ArrayList<>(List.of(gherkin.strip().split("\\R", -1)));
        lines = insertMissingScenarios(lines);
        return String.join("\n", indentStepsAndDocStrings(lines));
    }

    /** After every {@code @e_…} tag, ensure a {@code Scenario:} line exists before the first step. */
    private static List<String> insertMissingScenarios(List<String> in) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            String line = in.get(i);
            String t = line.strip();
            if (!t.matches("@e_[a-zA-Z0-9]+")) {
                out.add(line);
                continue;
            }
            out.add("  " + t);
            int j = i + 1;
            while (j < in.size() && in.get(j).isBlank()) {
                out.add(in.get(j));
                j++;
            }
            if (j < in.size()) {
                String next = in.get(j).strip();
                if (isStepKeywordLine(next) && !next.startsWith("Scenario:")) {
                    out.add("  Scenario: QA CoPilot scenario");
                }
            }
            i = j - 1;
        }
        return out;
    }

    private static boolean isStepKeywordLine(String stripped) {
        return stripped.startsWith("Given ")
                || stripped.startsWith("When ")
                || stripped.startsWith("Then ")
                || stripped.startsWith("And ");
    }

    private static List<String> indentStepsAndDocStrings(List<String> lines) {
        List<String> out = new ArrayList<>();
        boolean inDoc = false;
        for (String line : lines) {
            String t = line.strip();
            if (inDoc) {
                if (t.equals("\"\"\"")) {
                    inDoc = false;
                    out.add("      \"\"\"");
                } else {
                    out.add("        " + t);
                }
                continue;
            }
            if (t.equals("\"\"\"")
                    && !out.isEmpty()
                    && out.get(out.size() - 1).strip().endsWith("request body is:")) {
                inDoc = true;
                out.add("      \"\"\"");
                continue;
            }
            if (!t.isEmpty()
                    && line.equals(line.stripLeading())
                    && !t.startsWith("Feature:")
                    && !t.startsWith("Scenario:")
                    && !t.startsWith("@")
                    && isStepKeywordLine(t)) {
                out.add("    " + t);
                continue;
            }
            if (!t.isEmpty() && line.equals(line.stripLeading()) && t.startsWith("@e_")) {
                out.add("  " + t);
                continue;
            }
            if (!t.isEmpty() && line.equals(line.stripLeading()) && t.startsWith("Scenario:")) {
                out.add("  " + t);
                continue;
            }
            out.add(line);
        }
        return out;
    }
}

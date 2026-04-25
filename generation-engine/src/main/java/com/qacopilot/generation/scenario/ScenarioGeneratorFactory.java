package com.qacopilot.generation.scenario;

public final class ScenarioGeneratorFactory {

    private ScenarioGeneratorFactory() {
    }

    /**
     * Embedded / library use: template only. OpenAI-backed generation is wired through the sync CLI
     * ({@code --generator ai}) with {@code OPENAI_API_KEY}.
     */
    public static ScenarioGenerator fromCliId(String id) {
        if (id == null || id.isBlank() || "template".equalsIgnoreCase(id)) {
            return new TemplateScenarioGenerator();
        }
        throw new IllegalArgumentException(
                "ScenarioGeneratorFactory only supports 'template'. For AI scenarios use: "
                        + "java -jar ... sync --generator ai (and OPENAI_API_KEY)."
        );
    }
}

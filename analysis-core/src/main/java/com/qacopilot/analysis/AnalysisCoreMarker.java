package com.qacopilot.analysis;

/**
 * Marker class for the analysis-core module.
 * This will evolve into the Spring Boot source analysis engine.
 */
public final class AnalysisCoreMarker {

    private AnalysisCoreMarker() {
        // utility holder
    }

    public static String moduleName() {
        return "analysis-core";
    }
}

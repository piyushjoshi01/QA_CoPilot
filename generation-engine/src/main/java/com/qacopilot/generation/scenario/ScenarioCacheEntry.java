package com.qacopilot.generation.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioCacheEntry {
    public String signature;
    public String generatorId;
    public String gherkinBlock;

    public ScenarioCacheEntry() {
    }

    public ScenarioCacheEntry(String signature, String generatorId, String gherkinBlock) {
        this.signature = signature;
        this.generatorId = generatorId;
        this.gherkinBlock = gherkinBlock;
    }
}

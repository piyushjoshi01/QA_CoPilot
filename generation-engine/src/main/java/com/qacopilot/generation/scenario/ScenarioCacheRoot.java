package com.qacopilot.generation.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Jackson DTO for {@code .qacopilot/scenario-cache.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioCacheRoot {
    public int version = 1;
    public Map<String, ScenarioCacheEntry> byEndpointId = new LinkedHashMap<>();
}

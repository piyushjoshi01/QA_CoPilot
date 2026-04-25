package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.state.EndpointFingerprints;
import com.qacopilot.generation.state.ScenarioCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSynthesisIncrementalTest {

    @Test
    void cacheReusesUnchangedEndpoints(@TempDir Path tmp) throws Exception {
        Path qac = tmp.resolve(".qacopilot");
        Files.createDirectories(qac);
        ScenarioCache cache = ScenarioCache.loadOrEmpty(qac);

        var e1 = EndpointContract.synthetic("GET", "/a", "C", "a");
        var e2 = EndpointContract.synthetic("GET", "/b", "C", "b");
        List<EndpointContract> first = List.of(e1, e2);
        var source = new TemplateEndpointBlockSource();

        List<FeatureArtifact> f1 = FeatureSynthesis.buildIncremental(first, source, cache, false);
        String sid1 = EndpointFingerprints.stableId(e1);
        String block1 = cache.getGherkinIfFresh(sid1, EndpointFingerprints.signature(e1), "template");
        assertTrue(block1 != null && block1.contains("/a"));

        var e2changed = EndpointContract.synthetic("GET", "/b2", "C", "b");
        List<EndpointContract> second = List.of(e1, e2changed);

        List<FeatureArtifact> f2 = FeatureSynthesis.buildIncremental(second, source, cache, false);
        String merged =
                f2.stream().map(FeatureArtifact::content).collect(Collectors.joining("\n"));
        assertTrue(merged.contains("/a"));
        assertTrue(merged.contains("\"/b2\""));

        String reused = cache.getGherkinIfFresh(sid1, EndpointFingerprints.signature(e1), "template");
        assertEquals(block1, reused);
    }
}

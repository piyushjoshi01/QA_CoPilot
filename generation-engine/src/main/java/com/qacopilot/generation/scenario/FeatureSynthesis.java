package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.state.EndpointFingerprints;
import com.qacopilot.generation.state.ScenarioCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeatureSynthesis {

    /** @deprecated Legacy single-file path; domain layout uses {@code features/&lt;domain&gt;/&lt;domain&gt;.feature}. */
    @Deprecated public static final String GENERATED_FEATURE = "features/qacopilot.generated.feature";

    private FeatureSynthesis() {
    }

    public static List<FeatureArtifact> buildFresh(List<EndpointContract> endpoints, EndpointBlockSource source)
            throws Exception {
        Map<String, String> blocks = source.blocksFor(endpoints);
        return stitchAllDomains(endpoints, blocks);
    }

    /**
     * Reuses cached Gherkin per endpoint when {@link com.qacopilot.generation.state.EndpointFingerprints#signature}
     * is unchanged; only missing or stale endpoints are passed to {@code source}.
     */
    public static List<FeatureArtifact> buildIncremental(
            List<EndpointContract> endpoints,
            EndpointBlockSource source,
            ScenarioCache cache,
            boolean refreshAll
    ) throws Exception {
        Map<String, String> assembled = new LinkedHashMap<>();
        List<EndpointContract> toGenerate = new ArrayList<>();

        for (EndpointContract e : endpoints) {
            String sid = EndpointFingerprints.stableId(e);
            String sig = EndpointFingerprints.signature(e);
            if (!refreshAll) {
                String hit = cache.getGherkinIfFresh(sid, sig, source.id());
                if (hit != null) {
                    assembled.put(sid, hit);
                    continue;
                }
            }
            toGenerate.add(e);
        }

        if (!toGenerate.isEmpty()) {
            Map<String, String> fresh = source.blocksFor(toGenerate);
            for (EndpointContract e : toGenerate) {
                String sid = EndpointFingerprints.stableId(e);
                String sig = EndpointFingerprints.signature(e);
                String block = fresh.get(sid);
                if (block == null || block.isBlank()) {
                    throw new IllegalStateException("Generator " + source.id() + " did not produce a block for " + sig);
                }
                cache.put(sid, sig, source.id(), block);
                assembled.put(sid, block);
            }
        }

        Set<String> keep = new LinkedHashSet<>();
        for (EndpointContract e : endpoints) {
            keep.add(EndpointFingerprints.stableId(e));
        }
        cache.retainOnly(keep);
        cache.save();

        return stitchAllDomains(endpoints, assembled);
    }

    private static List<FeatureArtifact> stitchAllDomains(
            List<EndpointContract> endpoints,
            Map<String, String> byStableId
    ) {
        Map<String, List<EndpointContract>> byDomain = groupByDomain(endpoints);
        List<FeatureArtifact> out = new ArrayList<>();
        for (Map.Entry<String, List<EndpointContract>> e : byDomain.entrySet()) {
            String domainKey = e.getKey();
            List<EndpointContract> slice = e.getValue();
            String content = stitchOneDomain(domainKey, slice, byStableId);
            String rel = "features/" + domainKey + "/" + domainKey + ".feature";
            out.add(new FeatureArtifact(rel, content));
        }
        return out;
    }

    private static Map<String, List<EndpointContract>> groupByDomain(List<EndpointContract> endpoints) {
        Map<String, List<EndpointContract>> map = new LinkedHashMap<>();
        for (EndpointContract e : endpoints) {
            String key = DomainClassifier.domainKey(e);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    private static String stitchOneDomain(
            String domainKey,
            List<EndpointContract> endpoints,
            Map<String, String> byStableId
    ) {
        String title = DomainClassifier.domainTitle(domainKey);
        StringBuilder sb = new StringBuilder();
        sb.append("@domain_").append(domainKey).append("\n");
        sb.append("Feature: ").append(title).append(" API\n\n");
        for (EndpointContract e : endpoints) {
            String id = EndpointFingerprints.stableId(e);
            String block = byStableId.get(id);
            if (block == null || block.isBlank()) {
                throw new IllegalStateException("Missing Gherkin block for endpoint " + EndpointFingerprints.signature(e));
            }
            sb.append(block.strip()).append("\n\n");
        }
        return sb.toString().stripTrailing() + "\n";
    }
}

package com.qacopilot.generation.evolution;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Tracks changes to the API contract across pipeline runs.
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>On each sync run, the current set of endpoints is fingerprinted
 *       (stableId → SHA-256 of the full endpoint signature).</li>
 *   <li>The current fingerprints are compared with the previous {@link ContractSnapshot}.</li>
 *   <li>A {@link ContractDelta} is computed: ADDED / MODIFIED / DELETED / UNCHANGED per endpoint.</li>
 *   <li>Both the new snapshot and the delta are persisted via {@link SnapshotStore}.</li>
 * </ol>
 *
 * <p><strong>Performance + cost impact:</strong>
 * Only endpoints whose status is ADDED or MODIFIED need AI scenario regeneration.
 * The {@link ContractDelta.ImpactAnalysis} provides an estimate of tokens saved by not
 * re-prompting unchanged endpoints.
 *
 * <p><strong>JSON Patch approach:</strong>
 * The diff is intentionally a simple list of {@link EndpointChange} records rather than
 * RFC 6902 JSON Patch operations. This is easier to read in the output files, CI logs,
 * and the AI prompt context. The full contract JSON ({@code api-contract.json}) is always
 * written in its entirety — it is the delta file that is the "patch" representation.
 */
public final class ContractEvolutionTracker {

    private static final Logger LOG = Logger.getLogger(ContractEvolutionTracker.class.getName());
    private static final int TOKENS_PER_ENDPOINT = 350; // rough estimate for cost savings calc

    private final SnapshotStore store;

    public ContractEvolutionTracker(SnapshotStore store) {
        this.store = store;
    }

    /**
     * Record the current set of endpoints, compare with the last snapshot, and persist
     * both the new snapshot and the delta.
     *
     * @param endpoints   Current parsed endpoints.
     * @param projectName Project label (for snapshot metadata).
     * @return            The computed delta ({@code fromVersion} = 0 when there is no prior snapshot).
     */
    public ContractDelta track(List<EndpointContract> endpoints, String projectName) throws IOException {
        // Build current fingerprint map
        Map<String, String> currentFingerprints = fingerprintAll(endpoints);

        // Load previous snapshot
        Optional<ContractSnapshot> previous = store.loadLatestSnapshot();
        Map<String, String> previousFingerprints = previous.map(s -> s.endpoints).orElse(Map.of());
        int fromVersion = previous.map(s -> s.version).orElse(0);

        // Compute changes
        List<EndpointChange> changes = computeChanges(endpoints, currentFingerprints, previousFingerprints);

        // Build and save new snapshot
        int newVersion = store.nextVersion();
        ContractSnapshot snapshot = new ContractSnapshot(
                newVersion, Instant.now().toString(), projectName, currentFingerprints);
        store.saveSnapshot(snapshot);

        // Build and save delta
        ContractDelta delta = buildDelta(fromVersion, newVersion, changes, previousFingerprints.size());
        store.saveDelta(delta);

        logSummary(delta);
        return delta;
    }

    /**
     * Returns a list of stableIds that need AI scenario (re)generation based on the delta.
     * {@link ChangeType#ADDED} and {@link ChangeType#MODIFIED} endpoints must be regenerated;
     * {@link ChangeType#UNCHANGED} can use cached Gherkin.
     */
    public static List<String> endpointsNeedingRegeneration(ContractDelta delta) {
        List<String> ids = new ArrayList<>();
        for (EndpointChange c : delta.changes) {
            if (c.changeType == ChangeType.ADDED || c.changeType == ChangeType.MODIFIED) {
                ids.add(c.stableId);
            }
        }
        return ids;
    }

    // ── Diff computation ──────────────────────────────────────────────────────

    private static List<EndpointChange> computeChanges(
            List<EndpointContract> current,
            Map<String, String> currentMap,
            Map<String, String> previousMap) {

        List<EndpointChange> changes = new ArrayList<>();

        // ADDED and MODIFIED
        for (EndpointContract ep : current) {
            String sid = EndpointFingerprints.stableId(ep);
            String currentHash = currentMap.get(sid);
            String previousHash = previousMap.get(sid);

            if (previousHash == null) {
                changes.add(new EndpointChange(
                        sid, ChangeType.ADDED, ep.getHttpMethod(), ep.getPath(),
                        null, currentHash, "New endpoint"));
            } else if (!previousHash.equals(currentHash)) {
                changes.add(new EndpointChange(
                        sid, ChangeType.MODIFIED, ep.getHttpMethod(), ep.getPath(),
                        previousHash, currentHash, "Signature changed"));
            } else {
                changes.add(new EndpointChange(
                        sid, ChangeType.UNCHANGED, ep.getHttpMethod(), ep.getPath(),
                        previousHash, currentHash, null));
            }
        }

        // DELETED
        for (String sid : previousMap.keySet()) {
            if (!currentMap.containsKey(sid)) {
                changes.add(new EndpointChange(
                        sid, ChangeType.DELETED, "?", "?",
                        previousMap.get(sid), null, "Endpoint removed from codebase"));
            }
        }

        return changes;
    }

    private static ContractDelta buildDelta(
            int fromVersion, int toVersion,
            List<EndpointChange> changes,
            int previousEndpointCount) {

        ContractDelta delta = new ContractDelta();
        delta.fromVersion = fromVersion;
        delta.toVersion = toVersion;
        delta.timestamp = Instant.now().toString();
        delta.changes = changes;

        ContractDelta.DeltaSummary summary = new ContractDelta.DeltaSummary();
        summary.added = (int) changes.stream().filter(c -> c.changeType == ChangeType.ADDED).count();
        summary.modified = (int) changes.stream().filter(c -> c.changeType == ChangeType.MODIFIED).count();
        summary.deleted = (int) changes.stream().filter(c -> c.changeType == ChangeType.DELETED).count();
        summary.unchanged = (int) changes.stream().filter(c -> c.changeType == ChangeType.UNCHANGED).count();
        delta.summary = summary;

        ContractDelta.ImpactAnalysis impact = new ContractDelta.ImpactAnalysis();
        impact.scenariosToRegenerate = endpointsNeedingRegeneration(delta);
        int totalEndpoints = changes.size();
        int unchangedCount = summary.unchanged;
        impact.estimatedAiTokensSaved = unchangedCount * TOKENS_PER_ENDPOINT;
        impact.regenerationReason = String.format(
                "%d added + %d modified endpoint(s) require new scenarios; %d unchanged reuse cache",
                summary.added, summary.modified, unchangedCount);
        delta.impactAnalysis = impact;

        return delta;
    }

    // ── Fingerprinting ────────────────────────────────────────────────────────

    private static Map<String, String> fingerprintAll(List<EndpointContract> endpoints) {
        Map<String, String> map = new LinkedHashMap<>();
        for (EndpointContract ep : endpoints) {
            String sid = EndpointFingerprints.stableId(ep);
            String sig = EndpointFingerprints.signature(ep);
            map.put(sid, sha256(sig));
        }
        return map;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static void logSummary(ContractDelta delta) {
        ContractDelta.DeltaSummary s = delta.summary;
        LOG.info(String.format(
                "[ContractEvolutionTracker] v%d→v%d: +%d added, ~%d modified, -%d deleted, =%d unchanged | ~%d AI tokens saved",
                delta.fromVersion, delta.toVersion,
                s.added, s.modified, s.deleted, s.unchanged,
                delta.impactAnalysis != null ? delta.impactAnalysis.estimatedAiTokensSaved : 0));
    }
}

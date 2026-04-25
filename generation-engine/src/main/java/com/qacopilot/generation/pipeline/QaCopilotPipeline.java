package com.qacopilot.generation.pipeline;

import com.qacopilot.analysis.extractor.SpringAnalysisOptions;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientConfig;
import com.qacopilot.generation.ai.AIClientFactory;
import com.qacopilot.generation.evolution.ContractDelta;
import com.qacopilot.generation.evolution.ContractEvolutionTracker;
import com.qacopilot.generation.evolution.SnapshotStore;
import com.qacopilot.generation.openai.OpenAiConfig;
import com.qacopilot.generation.requirements.Requirement;
import com.qacopilot.generation.requirements.RequirementAwareBlockSource;
import com.qacopilot.generation.requirements.RequirementCoverageMapper;
import com.qacopilot.generation.requirements.RequirementDocument;
import com.qacopilot.generation.requirements.RequirementExtractor;
import com.qacopilot.generation.requirements.RequirementParserFactory;
import com.qacopilot.generation.requirements.RequirementStore;
import com.qacopilot.generation.scaffold.TestFrameworkScaffolder;
import com.qacopilot.generation.scenario.EndpointBlockSource;
import com.qacopilot.generation.scenario.FeatureArtifact;
import com.qacopilot.generation.scenario.FeatureSynthesis;
import com.qacopilot.generation.scenario.MultiModelEndpointBlockSource;
import com.qacopilot.generation.scenario.OpenAiEndpointBlockSource;
import com.qacopilot.generation.scenario.TemplateEndpointBlockSource;
import com.qacopilot.generation.state.ApiContractWriter;
import com.qacopilot.generation.state.LastEndpointsWriter;
import com.qacopilot.generation.state.ProjectEndpointExtractor;
import com.qacopilot.generation.state.QacopilotPaths;
import com.qacopilot.generation.state.ScenarioCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Programmatic entry point for the local sync pipeline (CLI and IDE plugin share this).
 *
 * <h3>Enhanced pipeline flow (all 3 features):</h3>
 * <ol>
 *   <li><strong>Code parsing</strong> — Spring endpoint extraction (incremental parse cache).</li>
 *   <li><strong>Contract evolution</strong> (Feature 3) — diff against previous snapshot;
 *       identify which endpoints changed.</li>
 *   <li><strong>Requirements ingestion</strong> (Feature 2) — parse document, extract structured
 *       requirements via AI, compute coverage map.</li>
 *   <li><strong>AI model selection</strong> (Feature 1) — route to the configured provider
 *       (OpenAI, Anthropic, Azure, local LLM) with retry + rate limiting + optional disk cache.</li>
 *   <li><strong>Scenario generation</strong> — incremental Gherkin generation with scenario cache;
 *       requirements-enriched prompt if requirements are present.</li>
 *   <li><strong>Scaffold update</strong> — write/update the standalone Cucumber+Rest Assured project.</li>
 * </ol>
 */
public final class QaCopilotPipeline {

    private static final Logger LOG = Logger.getLogger(QaCopilotPipeline.class.getName());

    private QaCopilotPipeline() {
    }

    public static PipelineSyncResult runSync(PipelineSyncRequest req) throws Exception {
        Path spring = req.springProjectRoot().toAbsolutePath().normalize();
        Path output = req.testFrameworkOutputDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(spring)) {
            throw new IllegalArgumentException("Spring project root is not a directory: " + spring);
        }

        // ── Step 1: Code parsing ──────────────────────────────────────────────
        Path parseCacheFile = QacopilotPaths.parseCacheJson(output);
        ProjectEndpointExtractor.Result scanResult =
                ProjectEndpointExtractor.extract(
                        spring, parseCacheFile,
                        req.includeGlobs(), req.excludeGlobs(),
                        req.springAnalysisOptions(), req.incrementalParse(),
                        req.openApiSpecPathsAbsolute(), req.discoverOpenApiSpecs());
        List<EndpointContract> endpoints = scanResult.endpoints();

        Path writeIr = req.writeIrSummaryPathOrNull();
        if (writeIr != null) {
            Path irPath = writeIr.toAbsolutePath().normalize();
            Files.createDirectories(irPath.getParent());
            Files.writeString(irPath, summarizeIr(endpoints));
        }

        Path qacDir = QacopilotPaths.dotDir(output);

        // ── Step 2: Contract evolution tracking (Feature 3) ───────────────────
        ContractDelta contractDelta = null;
        if (req.trackContractEvolution()) {
            contractDelta = trackEvolution(endpoints, spring, qacDir);
        }

        // ── Step 3: Requirements ingestion (Feature 2) ────────────────────────
        int requirementsCount = 0;
        List<Requirement> requirements = List.of();
        if (req.requirementsDocumentPathOrNull() != null) {
            requirements = ingestRequirements(req, endpoints, qacDir);
            requirementsCount = requirements.size();
        }

        // ── Step 4: AI model selection + block source construction (Feature 1) ─
        EndpointBlockSource blockSource = resolveBlockSource(req, requirements, qacDir);

        // ── Step 5: Incremental scenario generation ───────────────────────────
        ScenarioCache cache = ScenarioCache.loadOrEmpty(qacDir);
        List<FeatureArtifact> features =
                FeatureSynthesis.buildIncremental(
                        endpoints, blockSource, cache, req.scenarioRegenerateAll());

        // ── Step 6: Write state files + scaffold ─────────────────────────────
        String projectLabel = spring.getFileName() == null ? "spring-project" : spring.getFileName().toString();
        LastEndpointsWriter.write(QacopilotPaths.lastEndpointsJson(output), endpoints);
        ApiContractWriter.write(QacopilotPaths.apiContractJson(output), projectLabel, endpoints, scanResult.security());

        new TestFrameworkScaffolder()
                .ensureLayoutAndFeatures(output, features, blockSource.id(), req.forceScaffold());

        String scenarioMode = req.scenarioRegenerateAll() ? "regenerate-all" : "incremental";
        return new PipelineSyncResult(
                endpoints.size(),
                blockSource.id(),
                scenarioMode,
                output,
                qacDir,
                req.incrementalParse() ? parseCacheFile : null,
                requirementsCount,
                contractDelta);
    }

    // ── Feature 1: AI model selection ────────────────────────────────────────

    private static EndpointBlockSource resolveBlockSource(
            PipelineSyncRequest req,
            List<Requirement> requirements,
            Path qacDir) {

        String id = req.generatorId().toLowerCase(Locale.ROOT);

        if ("template".equals(id)) {
            return new TemplateEndpointBlockSource();
        }

        // Multi-model path (Feature 1): when aiClientConfig is provided
        if (req.aiClientConfig() != null) {
            Path cacheDir = req.enableAiResponseCache()
                    ? qacDir.resolve("ai-response-cache")
                    : null;
            AIClient aiClient = AIClientFactory.build(req.aiClientConfig(), cacheDir);

            // Requirements-enriched (Feature 2) if requirements are available
            if (!requirements.isEmpty()) {
                return new RequirementAwareBlockSource(aiClient, requirements, req.aiFallbackToTemplate());
            }
            return new MultiModelEndpointBlockSource(aiClient, req.aiFallbackToTemplate());
        }

        // Legacy "ai" path: resolve provider from env/flags via existing OpenAI config
        if ("ai".equals(id)) {
            // Check if env has non-OpenAI provider configured (transparent upgrade)
            String provider = System.getenv("AI_PROVIDER");
            if (provider != null && !provider.isBlank() && !"openai".equalsIgnoreCase(provider)) {
                AIClientConfig cfg = AIClientConfig.resolveFromEnv(
                        req.openaiApiKey(), req.openaiModel(), provider, req.aiFallbackToTemplate());
                Path cacheDir = req.enableAiResponseCache()
                        ? qacDir.resolve("ai-response-cache")
                        : null;
                AIClient aiClient = AIClientFactory.build(cfg, cacheDir);
                if (!requirements.isEmpty()) {
                    return new RequirementAwareBlockSource(aiClient, requirements, req.aiFallbackToTemplate());
                }
                return new MultiModelEndpointBlockSource(aiClient, req.aiFallbackToTemplate());
            }

            // Default: legacy OpenAI path (fully backward-compatible)
            OpenAiConfig cfg = OpenAiConfig.resolve(req.openaiApiKey(), req.openaiModel(), req.aiFallbackToTemplate());
            return new OpenAiEndpointBlockSource(cfg);
        }

        throw new IllegalArgumentException("Unknown generator: '" + req.generatorId() + "' (use: template, ai)");
    }

    // ── Feature 2: Requirements ingestion ────────────────────────────────────

    private static List<Requirement> ingestRequirements(
            PipelineSyncRequest req,
            List<EndpointContract> endpoints,
            Path qacDir) {
        Path docPath = req.requirementsDocumentPathOrNull();
        try {
            LOG.info("[QaCopilotPipeline] Ingesting requirements from: " + docPath);
            RequirementDocument doc = RequirementParserFactory.parse(docPath);
            if (!doc.hasContent()) {
                LOG.warning("[QaCopilotPipeline] Requirements document has no readable content: " + docPath);
                return List.of();
            }

            // Use the configured AI client to extract structured requirements
            AIClient aiClient = resolveAiClientForRequirements(req, qacDir);
            RequirementExtractor extractor = new RequirementExtractor(aiClient);
            List<Requirement> requirements = extractor.extract(doc);

            // Map requirements to endpoints and write coverage report
            Path reqDir = qacDir.resolve("requirements");
            requirements = RequirementCoverageMapper.mapAndReport(requirements, endpoints, reqDir);

            // Persist to disk
            RequirementStore store = new RequirementStore(qacDir);
            store.save(doc.fileName(), requirements);

            LOG.info("[QaCopilotPipeline] Extracted " + requirements.size()
                    + " requirements, wrote coverage-map to " + reqDir);
            return requirements;
        } catch (Exception e) {
            LOG.warning("[QaCopilotPipeline] Requirements ingestion failed: " + e.getMessage()
                    + " — continuing without requirements context.");
            return List.of();
        }
    }

    private static AIClient resolveAiClientForRequirements(PipelineSyncRequest req, Path qacDir) {
        if (req.aiClientConfig() != null) {
            return AIClientFactory.build(req.aiClientConfig(),
                    req.enableAiResponseCache() ? qacDir.resolve("ai-response-cache") : null);
        }
        // Fall back to an OpenAI config derived from env vars
        AIClientConfig cfg = AIClientConfig.forOpenAI(req.openaiApiKey(), req.openaiModel(), true);
        return AIClientFactory.build(cfg, null);
    }

    // ── Feature 3: Contract evolution ────────────────────────────────────────

    private static ContractDelta trackEvolution(
            List<EndpointContract> endpoints, Path spring, Path qacDir) {
        try {
            String projectName = spring.getFileName() == null ? "project" : spring.getFileName().toString();
            SnapshotStore store = new SnapshotStore(qacDir);
            ContractEvolutionTracker tracker = new ContractEvolutionTracker(store);
            ContractDelta delta = tracker.track(endpoints, projectName);
            LOG.info("[QaCopilotPipeline] Evolution: v" + delta.fromVersion + "→v" + delta.toVersion
                    + " | +" + delta.summary.added + " ~" + delta.summary.modified + " -" + delta.summary.deleted);
            return delta;
        } catch (IOException e) {
            LOG.warning("[QaCopilotPipeline] Failed to track contract evolution: " + e.getMessage());
            return null;
        }
    }

    // ── IR summary ────────────────────────────────────────────────────────────

    private static String summarizeIr(List<EndpointContract> endpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: 0.1 (summary)\n");
        sb.append("endpoints:\n");
        for (EndpointContract e : endpoints) {
            sb.append("  - method: ").append(e.getHttpMethod())
                    .append("\n    path: ").append(e.getPath())
                    .append("\n    controller: ").append(e.getControllerClassName())
                    .append("\n    handler: ").append(e.getHandlerMethodName())
                    .append("\n");
        }
        return sb.toString();
    }
}

package com.qacopilot.generation.cli;

import com.qacopilot.analysis.extractor.SpringAnalysisOptions;
import com.qacopilot.generation.ai.AIClientConfig;
import com.qacopilot.generation.ai.AIProvider;
import com.qacopilot.generation.pipeline.PipelineSyncRequest;
import com.qacopilot.generation.pipeline.PipelineSyncResult;
import com.qacopilot.generation.pipeline.QaCopilotPipeline;
import com.qacopilot.generation.scenario.AiGherkinSanitizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Local-only pipeline: scan a Spring Boot project, generate/update a standalone {@code test_framework}
 * directory (any path), then you run Gradle in that folder for Cucumber + Gradle HTML reports.
 *
 * <p>Scenario text is cached per endpoint under {@code .qacopilot/scenario-cache.json} so unchanged
 * controllers are not regenerated. Use {@code --scenario-mode regenerate-all} to ignore the cache.</p>
 */
public final class LocalPipelineCli {

    private LocalPipelineCli() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (CliException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void repairGherkin(String[] args) throws Exception {
        Map<String, List<String>> opts = parseOpts(args);
        Path output = requirePath(opts, "--output");
        Path featuresRoot = output.resolve("src/test/resources/features");
        if (!Files.isDirectory(featuresRoot)) {
            throw new CliException("Directory not found: " + featuresRoot);
        }
        try (Stream<Path> walk = Files.walk(featuresRoot)) {
            List<Path> featureFiles =
                    walk.filter(p -> p.toString().endsWith(".feature")).sorted().toList();
            if (featureFiles.isEmpty()) {
                throw new CliException("No .feature files under " + featuresRoot);
            }
            int n = 0;
            for (Path feature : featureFiles) {
                String original = Files.readString(feature);
                String fixed = AiGherkinSanitizer.sanitize(original);
                Files.writeString(feature, fixed);
                System.out.println("Rewrote (AI Gherkin repair): " + feature.toAbsolutePath());
                if (original.equals(fixed)) {
                    System.out.println("  (no changes — already valid or unsupported pattern.)");
                } else {
                    n++;
                }
            }
            System.out.println("Done. " + n + " file(s) had content changes.");
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String cmd = args[0];
        if ("help".equals(cmd) || "--help".equals(cmd) || "-h".equals(cmd)) {
            printUsage();
            return;
        }
        if ("repair-gherkin".equals(cmd)) {
            repairGherkin(slice(args, 1));
            return;
        }
        if (!"sync".equals(cmd)) {
            throw new CliException("Unknown command: " + cmd + ". Try: sync, repair-gherkin, help");
        }
        Map<String, List<String>> opts = parseOpts(slice(args, 1));
        Path spring = requirePath(opts, "--spring-project");
        Path output = requirePath(opts, "--output");
        if (!Files.isDirectory(spring)) {
            throw new CliException("--spring-project is not a directory: " + spring);
        }

        String generatorId = firstOrDefault(opts, "--generator", "template").toLowerCase(Locale.ROOT);
        String scenarioMode = firstOrDefault(opts, "--scenario-mode", "incremental").toLowerCase(Locale.ROOT);
        boolean refreshAll = "regenerate-all".equals(scenarioMode) || "all".equals(scenarioMode);
        if (!"incremental".equals(scenarioMode) && !refreshAll) {
            throw new CliException("--scenario-mode must be incremental or regenerate-all");
        }

        boolean forceScaffold = flag(opts, "--force-scaffold");
        boolean aiFallback = flag(opts, "--ai-fallback-template");
        String openaiKey = firstOrDefault(opts, "--openai-api-key", "");
        String openaiModel = firstOrDefault(opts, "--openai-model", "");

        List<String> includes = opts.getOrDefault("--include", List.of());
        List<String> excludes = opts.getOrDefault("--exclude", List.of());

        boolean incrementalParse = !flag(opts, "--no-incremental-parse");
        SpringAnalysisOptions analysisOptions = buildAnalysisOptions(opts);
        List<Path> openApiSpecs = buildOpenApiSpecPaths(opts, spring);
        boolean discoverOpenApi = !flag(opts, "--no-openapi-discover");
        List<String> writeIr = opts.get("--write-ir");
        Path writeIrPath =
                writeIr != null && !writeIr.isEmpty()
                        ? Path.of(writeIr.get(0)).toAbsolutePath().normalize()
                        : null;

        // ── Feature 1: Multi-model AI provider ───────────────────────────────
        AIClientConfig aiClientConfig = buildAiClientConfig(opts, generatorId, openaiKey, openaiModel, aiFallback);
        boolean enableAiCache = flag(opts, "--ai-response-cache");

        // ── Feature 2: Requirements document ingestion ────────────────────────
        String reqDocStr = firstOrDefault(opts, "--requirements-doc", "");
        Path requirementsDoc = reqDocStr.isBlank() ? null
                : Path.of(reqDocStr).toAbsolutePath().normalize();

        // ── Feature 3: Contract evolution tracking ────────────────────────────
        boolean trackEvolution = flag(opts, "--track-evolution");

        PipelineSyncRequest pipelineReq =
                new PipelineSyncRequest(
                        spring,
                        output,
                        generatorId,
                        refreshAll,
                        forceScaffold,
                        aiFallback,
                        openaiKey,
                        openaiModel,
                        includes,
                        excludes,
                        incrementalParse,
                        analysisOptions,
                        openApiSpecs,
                        discoverOpenApi,
                        writeIrPath,
                        aiClientConfig,
                        enableAiCache,
                        requirementsDoc,
                        trackEvolution
                );

        if ("ai".equals(generatorId.toLowerCase(Locale.ROOT)) && aiClientConfig == null && openaiKey.isBlank()) {
            System.out.println(
                    "(API key: env OPENAI_API_KEY or AI_API_KEY; provider: env AI_PROVIDER — "
                            + "or use --ai-provider / --ai-api-key / --ai-model flags)");
        }

        PipelineSyncResult done = QaCopilotPipeline.runSync(pipelineReq);
        if (writeIrPath != null) {
            System.out.println("Wrote IR summary: " + writeIrPath);
        }
        if (done.endpointCount() == 0) {
            System.err.println("Warning: no REST endpoints found (GET/POST/PUT/DELETE on @RestController).");
        }

        System.out.println("Updated test framework at: " + done.testFrameworkOutputDirectory().toAbsolutePath());
        System.out.println(
                "Endpoints: "
                        + done.endpointCount()
                        + " | generator: "
                        + done.generatorId()
                        + " | scenarios: "
                        + done.scenarioModeLabel()
        );
        if (done.requirementsExtracted() > 0) {
            System.out.println("Requirements: " + done.requirementsExtracted() + " extracted → .qacopilot/requirements/");
        }
        if (done.contractDeltaOrNull() != null) {
            var delta = done.contractDeltaOrNull();
            System.out.printf("Evolution:    v%d→v%d | +%d added, ~%d modified, -%d deleted | ~%d AI tokens saved%n",
                    delta.fromVersion, delta.toVersion,
                    delta.summary.added, delta.summary.modified, delta.summary.deleted,
                    delta.impactAnalysis != null ? delta.impactAnalysis.estimatedAiTokensSaved : 0);
        }
        System.out.println("State: " + done.qacopilotStateDirectory().resolve("scenario-cache.json"));
        if (done.parseCacheFileOrNull() != null) {
            System.out.println("Parse cache: " + done.parseCacheFileOrNull().toAbsolutePath());
        }
        System.out.println();
        System.out.println("Next (from the output folder):");
        System.out.println("  ./gradlew test -Dqacopilot.dryRun=true");
        System.out.println("  open build/reports/tests/test/index.html");
        System.out.println("  open build/reports/qacopilot-api/report.html   # API URL, params, bodies, expected vs actual");
        System.out.println();
        System.out.println("Against a running API:");
        System.out.println("  ./gradlew test -Dapi.base.url=http://localhost:8080");
        System.out.println("Filter by Cucumber tags:");
        System.out.println("  ./gradlew test -Dcucumber.filter.tags=@domain_user");
    }

    /**
     * Build an {@link AIClientConfig} from CLI flags, or return {@code null} to use the legacy
     * OpenAI-only path (backward compat when no --ai-provider is given).
     */
    private static AIClientConfig buildAiClientConfig(
            Map<String, List<String>> opts,
            String generatorId,
            String openaiKey,
            String openaiModel,
            boolean fallback) {

        if (!"ai".equals(generatorId.toLowerCase(Locale.ROOT))) {
            return null; // template generator — no AI client needed
        }

        String provider = firstOrDefault(opts, "--ai-provider", "");
        String aiKey = firstOrDefault(opts, "--ai-api-key", openaiKey);
        String aiModel = firstOrDefault(opts, "--ai-model", openaiModel);
        String baseUrl = firstOrDefault(opts, "--ai-base-url", "");
        String apiVersion = firstOrDefault(opts, "--ai-api-version", "");

        // If no explicit provider flag but also no AI_PROVIDER env, return null → legacy path
        if (provider.isBlank() && (System.getenv("AI_PROVIDER") == null || System.getenv("AI_PROVIDER").isBlank())) {
            return null;
        }

        return AIClientConfig.resolveFromEnv(aiKey, aiModel, provider, fallback);
    }

    private static boolean flag(Map<String, List<String>> opts, String key) {
        List<String> v = opts.get(key);
        return v != null && !v.isEmpty();
    }

    private static List<Path> buildOpenApiSpecPaths(Map<String, List<String>> opts, Path springProject) {
        List<Path> out = new ArrayList<>();
        for (String s : opts.getOrDefault("--openapi-spec", List.of())) {
            if (s == null || s.isBlank()) {
                continue;
            }
            Path p = Path.of(s);
            if (!p.isAbsolute()) {
                p = springProject.resolve(p);
            }
            out.add(p.toAbsolutePath().normalize());
        }
        return out;
    }

    private static SpringAnalysisOptions buildAnalysisOptions(Map<String, List<String>> opts) {
        SpringAnalysisOptions.Builder b = SpringAnalysisOptions.builder();
        if (flag(opts, "--no-symbol-solver")) {
            b.symbolSolverEnabled(false);
        }
        for (String jar : opts.getOrDefault("--analysis-jar", List.of())) {
            if (jar != null && !jar.isBlank()) {
                b.addJarClasspathEntry(Path.of(jar).toAbsolutePath().normalize());
            }
        }
        for (String root : opts.getOrDefault("--analysis-src-root", List.of())) {
            if (root != null && !root.isBlank()) {
                b.addSourceRoot(Path.of(root).toAbsolutePath().normalize());
            }
        }
        return b.build();
    }

    private static Path requirePath(Map<String, List<String>> opts, String key) {
        List<String> v = opts.get(key);
        if (v == null || v.isEmpty() || v.get(0).isBlank()) {
            throw new CliException("Missing required option: " + key);
        }
        return Path.of(v.get(0)).toAbsolutePath().normalize();
    }

    private static String firstOrDefault(Map<String, List<String>> opts, String key, String def) {
        List<String> v = opts.get(key);
        if (v == null || v.isEmpty()) {
            return def;
        }
        return v.get(0);
    }

    private static String[] slice(String[] arr, int start) {
        String[] out = new String[arr.length - start];
        System.arraycopy(arr, start, out, 0, out.length);
        return out;
    }

    private static Map<String, List<String>> parseOpts(String[] args) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new CliException("Unexpected argument: " + a);
            }
            String key;
            String value = null;
            if (a.contains("=")) {
                int eq = a.indexOf('=');
                key = a.substring(0, eq);
                value = a.substring(eq + 1);
            } else {
                key = a;
            }
            if (value == null) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                } else {
                    value = "true";
                }
            }
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    private static void printUsage() {
        System.out.println(
                """
                        QA CoPilot — local pipeline

                        Usage:
                          java -jar generation-engine-*-all.jar repair-gherkin \\
                            --output <test_framework_directory>

                          java -jar generation-engine-*-all.jar sync \\
                            --spring-project <path> \\
                            --output <test_framework_directory> \\
                            [--generator template|ai] \\
                            [--scenario-mode incremental|regenerate-all] \\
                            [--force-scaffold] \\

                        ── Legacy AI flags (OpenAI only) ──────────────────────────────────────
                            [--openai-api-key <key>] [--openai-model <model>] [--ai-fallback-template]

                        ── Feature 1: Multi-Model AI ──────────────────────────────────────────
                            [--ai-provider openai|anthropic|azure-openai|local-llm]
                            [--ai-api-key <key>]        Override for the selected provider
                            [--ai-model <model>]        e.g. claude-3-5-sonnet-20241022, llama3.2
                            [--ai-base-url <url>]       Azure resource URL or local LLM endpoint
                            [--ai-api-version <ver>]    Azure API version (default 2024-02-01)
                            [--ai-response-cache]       Cache raw AI responses to .qacopilot/ai-response-cache/
                            Env: AI_PROVIDER, AI_API_KEY, AI_MODEL, AI_BASE_URL, AI_API_VERSION
                                 AI_MAX_TOKENS, AI_TEMPERATURE, AI_TIMEOUT_SECONDS, AI_MAX_RETRIES

                        ── Feature 2: Requirements Ingestion ──────────────────────────────────
                            [--requirements-doc <path>] Path to requirements (.txt, .md, .pdf, .docx)
                            Output: .qacopilot/requirements/requirements.json
                                    .qacopilot/requirements/coverage-map.json

                        ── Feature 3: Contract Evolution Tracking ─────────────────────────────
                            [--track-evolution]         Snapshot + diff on every sync run
                            Output: .qacopilot/evolution/snapshot-vN.json
                                    .qacopilot/evolution/delta-vN-to-vM.json

                        ── Code analysis ──────────────────────────────────────────────────────
                            [--include <glob>]... [--exclude <glob>]...
                            [--write-ir <file>]
                            [--no-incremental-parse] [--no-symbol-solver]
                            [--analysis-jar <path>]... [--analysis-src-root <path>]...
                            [--openapi-spec <path>]... [--no-openapi-discover]

                        State under the test framework:
                          .qacopilot/scenario-cache.json         Cached Gherkin per endpoint
                          .qacopilot/parse-cache.json            Per-file SHA-256 parse cache
                          .qacopilot/api-contract.json           Full API contract (schema v4)
                          .qacopilot/ai-response-cache/          Raw AI response cache (opt-in)
                          .qacopilot/requirements/               Extracted requirements + coverage
                          .qacopilot/evolution/                  Versioned snapshots + deltas

                        Examples:
                          # Template (no AI):
                          java -jar .../generation-engine-*-all.jar sync \\
                            --spring-project ~/work/my-api --output ~/work/my-api-tests

                          # OpenAI (legacy path — fully backward compatible):
                          OPENAI_API_KEY=sk-... java -jar .../generation-engine-*-all.jar sync \\
                            --spring-project ~/work/my-api --output ~/work/my-api-tests \\
                            --generator ai --ai-fallback-template

                          # Anthropic Claude with requirements ingestion + evolution tracking:
                          AI_PROVIDER=anthropic AI_API_KEY=sk-ant-... \\
                          java -jar .../generation-engine-*-all.jar sync \\
                            --spring-project ~/work/my-api --output ~/work/my-api-tests \\
                            --generator ai --ai-model claude-3-5-sonnet-20241022 \\
                            --requirements-doc ~/work/docs/requirements.pdf \\
                            --track-evolution --ai-response-cache

                          # Local Ollama (no API key required):
                          java -jar .../generation-engine-*-all.jar sync \\
                            --spring-project ~/work/my-api --output ~/work/my-api-tests \\
                            --generator ai --ai-provider local-llm --ai-model llama3.2

                          # Azure OpenAI:
                          java -jar .../generation-engine-*-all.jar sync \\
                            --spring-project ~/work/my-api --output ~/work/my-api-tests \\
                            --generator ai --ai-provider azure-openai \\
                            --ai-api-key <azure-key> \\
                            --ai-base-url https://myresource.openai.azure.com/openai/deployments/gpt-4o \\
                            --ai-api-version 2024-02-01
                        """
        );
    }

    private static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }
}

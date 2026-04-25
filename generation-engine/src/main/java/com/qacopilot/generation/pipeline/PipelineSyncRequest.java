package com.qacopilot.generation.pipeline;

import com.qacopilot.analysis.extractor.SpringAnalysisOptions;
import com.qacopilot.generation.ai.AIClientConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Input for {@link QaCopilotPipeline#runSync(PipelineSyncRequest)} — same work as {@code LocalPipelineCli sync}.
 *
 * <h3>New fields (backward-compat — all nullable/false-defaulted):</h3>
 * <ul>
 *   <li>{@link #aiClientConfig} — multi-model AI configuration (Feature 1).
 *       When {@code null}, the pipeline falls back to the legacy {@link #openaiApiKey}/{@link #openaiModel} path.</li>
 *   <li>{@link #requirementsDocumentPathOrNull} — path to a requirements doc to ingest (Feature 2).
 *       Supports {@code .txt}, {@code .md}, {@code .pdf}, {@code .docx}.</li>
 *   <li>{@link #trackContractEvolution} — whether to record a versioned snapshot and delta (Feature 3).</li>
 *   <li>{@link #enableAiResponseCache} — whether to cache raw AI responses to disk (Feature 1 opt-in).</li>
 * </ul>
 */
public record PipelineSyncRequest(
        Path springProjectRoot,
        Path testFrameworkOutputDirectory,
        String generatorId,
        boolean scenarioRegenerateAll,
        boolean forceScaffold,
        boolean aiFallbackToTemplate,
        String openaiApiKey,
        String openaiModel,
        List<String> includeGlobs,
        List<String> excludeGlobs,
        boolean incrementalParse,
        SpringAnalysisOptions springAnalysisOptions,
        List<Path> openApiSpecPathsAbsolute,
        boolean discoverOpenApiSpecs,
        Path writeIrSummaryPathOrNull,
        // ── Feature 1: Multi-model AI ─────────────────────────────────────────
        AIClientConfig aiClientConfig,
        boolean enableAiResponseCache,
        // ── Feature 2: Requirements ingestion ────────────────────────────────
        Path requirementsDocumentPathOrNull,
        // ── Feature 3: Contract evolution tracking ────────────────────────────
        boolean trackContractEvolution
) {
    public PipelineSyncRequest {
        if (springProjectRoot == null || testFrameworkOutputDirectory == null) {
            throw new IllegalArgumentException("spring and output paths are required");
        }
        if (generatorId == null || generatorId.isBlank()) {
            throw new IllegalArgumentException("generatorId is required");
        }
        if (springAnalysisOptions == null) {
            throw new IllegalArgumentException("springAnalysisOptions is required");
        }
        includeGlobs = includeGlobs == null ? List.of() : List.copyOf(includeGlobs);
        excludeGlobs = excludeGlobs == null ? List.of() : List.copyOf(excludeGlobs);
        openApiSpecPathsAbsolute =
                openApiSpecPathsAbsolute == null ? List.of() : List.copyOf(openApiSpecPathsAbsolute);
        openaiApiKey = openaiApiKey == null ? "" : openaiApiKey;
        openaiModel = openaiModel == null ? "" : openaiModel;
        // aiClientConfig, requirementsDocumentPathOrNull may be null — treated as "feature disabled"
    }
}

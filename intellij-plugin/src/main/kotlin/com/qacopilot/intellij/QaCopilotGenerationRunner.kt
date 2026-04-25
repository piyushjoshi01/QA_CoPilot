package com.qacopilot.intellij

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.qacopilot.generation.ai.AIClientConfig
import com.qacopilot.generation.ai.AIProvider
import com.qacopilot.generation.pipeline.PipelineSyncRequest
import com.qacopilot.generation.pipeline.QaCopilotPipeline
import com.qacopilot.intellij.settings.QaCopilotProjectSettings
import com.qacopilot.intellij.state.QaCopilotLastRunService
import com.qacopilot.intellij.ui.QaCopilotUiNotifier
import com.qacopilot.intellij.util.IntellijAnalysisOptionsFactory
import com.qacopilot.intellij.util.QaCopilotPathUtil
import java.nio.file.Files
import java.nio.file.Path

object QaCopilotGenerationRunner {

    fun submitGenerate(project: Project) {
        val basePath = project.basePath ?: return
        val st = QaCopilotProjectSettings.getInstance(project).state
        val springRoot = Path.of(basePath)
        val outputRaw = st.outputPath.trim().ifEmpty { "qa-tests" }
        val candidate = Path.of(outputRaw)
        val output =
            if (candidate.isAbsolute) candidate.normalize()
            else springRoot.resolve(outputRaw).normalize()
        val generator = st.generatorId.trim().ifEmpty { "template" }.lowercase()
        val scenarioRegenerateAll = !st.incrementalScenarioMode

        // Validate requirements document before starting the background task
        val requirementsDocPath = resolveRequirementsDoc(st)
        if (requirementsDocPath != null && !Files.isRegularFile(requirementsDocPath)) {
            notify(
                project,
                "QA CoPilot — Invalid requirements document",
                "File not found: ${requirementsDocPath}\n" +
                "Check the path in Settings → Tools → QA CoPilot → Business Requirements.",
                NotificationType.WARNING,
            )
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "QA CoPilot", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    try {
                        // Phase 1: endpoint extraction
                        indicator.text = "Scanning Spring endpoints…"
                        val analysisOptions = IntellijAnalysisOptionsFactory.fromProject(project)
                        val aiClientConfig = buildAiClientConfig(st, generator)

                        // Phase 2: requirements ingestion feedback
                        if (requirementsDocPath != null) {
                            indicator.text = "Ingesting requirements from ${requirementsDocPath.fileName}…"
                        }

                        val req = PipelineSyncRequest(
                            springRoot,
                            output,
                            generator,
                            scenarioRegenerateAll,
                            false,
                            st.aiFallbackToTemplate,
                            st.openaiApiKey,        // legacy field — kept for backward compat
                            st.openaiModel,         // legacy field
                            emptyList(),
                            emptyList(),
                            st.incrementalParse,
                            analysisOptions,
                            emptyList(),
                            true,
                            null,
                            // Feature 1: multi-model AI
                            aiClientConfig,
                            st.aiResponseCache,
                            // Feature 2: business requirements document
                            requirementsDocPath,
                            // Feature 3: evolution tracking (off by default in IDE)
                            false,
                        )

                        indicator.text = "Generating QA scenarios…"
                        val result = QaCopilotPipeline.runSync(req)

                        // Derive requirements display info
                        val reqFile = requirementsDocPath?.fileName?.toString() ?: ""
                        val reqCount = result.requirementsExtracted()
                        val reqMapped = if (reqCount > 0) reqCount else -1   // -1 = not used

                        ApplicationManager.getApplication().invokeLater {
                            QaCopilotLastRunService.getInstance(project).recordSuccess(
                                endpointCount = result.endpointCount(),
                                outputPath = result.testFrameworkOutputDirectory().toString(),
                                generatorId = result.generatorId(),
                                scenarioModeLabel = result.scenarioModeLabel(),
                                requirementsCount = if (requirementsDocPath != null) reqCount else -1,
                                requirementsFile = reqFile,
                                requirementsMapped = reqMapped.coerceAtLeast(0),
                            )
                            QaCopilotUiNotifier.notifyGenerationFinished(project)

                            val summary = buildString {
                                append("Endpoints: ${result.endpointCount()}")
                                if (requirementsDocPath != null && reqCount > 0) {
                                    append(" · Requirements: $reqCount extracted from $reqFile")
                                }
                            }
                            notify(
                                project,
                                "QA CoPilot finished",
                                "$summary\nOutput: ${result.testFrameworkOutputDirectory()}",
                                NotificationType.INFORMATION,
                            )
                        }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            notify(
                                project,
                                "QA CoPilot failed",
                                ex.message ?: ex.javaClass.simpleName,
                                NotificationType.ERROR,
                            )
                        }
                    }
                }
            },
        )
    }

    // ── Requirements document resolution ─────────────────────────────────────

    /**
     * Returns the requirements doc path if requirements ingestion is enabled and a path
     * is configured, otherwise null (feature disabled).
     */
    private fun resolveRequirementsDoc(st: QaCopilotProjectSettings.State): Path? {
        if (!st.requirementsEnabled) return null
        val raw = st.requirementsDocPath.trim()
        if (raw.isBlank()) return null
        return Path.of(raw).toAbsolutePath().normalize()
    }

    // ── AI client config ──────────────────────────────────────────────────────

    /**
     * Builds an [AIClientConfig] from settings when generator = "ai".
     * Returns null when generator = "template" or no provider is configured.
     */
    private fun buildAiClientConfig(
        st: QaCopilotProjectSettings.State,
        generator: String,
    ): AIClientConfig? {
        if (generator != "ai") return null

        val provider = try {
            AIProvider.fromId(st.aiProvider.trim().ifBlank { "openai" })
        } catch (e: IllegalArgumentException) {
            AIProvider.OPENAI
        }

        val apiKey = st.aiApiKey.trim().ifBlank { st.openaiApiKey.trim() }
        val model = st.aiModel.trim().ifBlank {
            st.openaiModel.trim().ifBlank { defaultModelFor(provider) }
        }

        return AIClientConfig(
            /* provider         */ provider,
            /* apiKey           */ apiKey,
            /* model            */ model,
            /* baseUrl          */ st.aiBaseUrl.trim(),
            /* apiVersion       */ st.aiApiVersion.trim(),
            /* extraHeaders     */ emptyMap(),
            /* temperature      */ 0.38,
            /* maxTokens        */ 16_384,
            /* timeoutSeconds   */ 120,
            /* maxRetries       */ 3,
            /* rateLimitDelayMs */ 350L,
            /* fallbackToTemplate */ st.aiFallbackToTemplate,
        )
    }

    private fun defaultModelFor(provider: AIProvider): String = when (provider) {
        AIProvider.OPENAI -> "gpt-4o-mini"
        AIProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
        AIProvider.AZURE_OPENAI -> ""
        AIProvider.LOCAL_LLM -> "llama3.2"
        AIProvider.HUGGING_FACE -> ""
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun notify(project: Project, title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(Notification("QA CoPilot", title, content, type), project)
    }

    fun resolvedOutputDirectory(project: Project): Path? = QaCopilotPathUtil.resolveFrameworkOutput(project)
}

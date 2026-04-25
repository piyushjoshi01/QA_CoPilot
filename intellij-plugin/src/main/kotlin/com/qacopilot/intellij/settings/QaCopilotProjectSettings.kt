package com.qacopilot.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(
    name = "QaCopilotProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class QaCopilotProjectSettings : PersistentStateComponent<QaCopilotProjectSettings.State> {

    class State {
        /** Relative to project base path, or absolute path. */
        var outputPath: String = "qa-tests"
        var generatorId: String = "template"
        var incrementalScenarioMode: Boolean = true
        var incrementalParse: Boolean = true
        var aiFallbackToTemplate: Boolean = true

        // ── Legacy OpenAI-only fields (kept for backward compat) ──────────────
        /** @deprecated Prefer aiApiKey + aiProvider = "openai". */
        var openaiApiKey: String = ""
        /** @deprecated Prefer aiModel + aiProvider = "openai". */
        var openaiModel: String = ""

        // ── Multi-model AI configuration (Feature 1) ──────────────────────────
        /**
         * AI provider id. One of: openai, anthropic, azure-openai, local-llm.
         * Defaults to "openai" for backward compatibility.
         */
        var aiProvider: String = "openai"

        /**
         * API key for the selected provider.
         * - OpenAI: sk-...
         * - Anthropic: sk-ant-...
         * - Azure OpenAI: resource key
         * - Local LLM: leave blank (not required)
         */
        var aiApiKey: String = ""

        /**
         * Model name for the selected provider.
         * - OpenAI:       gpt-4o-mini, gpt-4o, gpt-4-turbo
         * - Anthropic:    claude-3-5-sonnet-20241022, claude-3-haiku-20240307
         * - Azure OpenAI: your deployment name (e.g. gpt-4o)
         * - Local LLM:    llama3.2, mistral, phi3, deepseek-r1:7b
         */
        var aiModel: String = ""

        /**
         * Base URL override.
         * - OpenAI:       leave blank (uses api.openai.com)
         * - Anthropic:    leave blank (uses api.anthropic.com)
         * - Azure OpenAI: https://<resource>.openai.azure.com/openai/deployments/<deployment>
         * - Local LLM:    http://localhost:11434/v1  (Ollama default)
         */
        var aiBaseUrl: String = ""

        /**
         * API version — Azure OpenAI only (e.g. 2024-02-01).
         * Leave blank for all other providers.
         */
        var aiApiVersion: String = ""

        /** Cache raw AI responses to disk (.qacopilot/ai-response-cache/). */
        var aiResponseCache: Boolean = false

        // ── Business requirements ingestion (Feature 2) ───────────────────────
        /**
         * Absolute path to the business requirements document to ingest on every sync run.
         * Supported formats: .txt, .md, .pdf (requires PDFBox), .docx (requires Apache POI).
         */
        var requirementsDocPath: String = ""

        /**
         * Whether to run requirements ingestion during sync.
         * When true, the AI prompt is enriched with matching business requirements so that
         * generated Gherkin scenarios explicitly cover acceptance criteria and edge cases.
         */
        var requirementsEnabled: Boolean = false

        // ── Test execution ────────────────────────────────────────────────────
        /** Shown in settings; tests use -Dapi.base.url= when you run Gradle in the output folder. */
        var defaultApiBaseUrl: String = "http://localhost:8080"
        var defaultAuthToken: String = ""
        /** Default Cucumber tag expression for run configurations and the tool window. */
        var cucumberFilterTags: String = ""
    }

    private var internalState = State()

    override fun getState(): State = internalState

    override fun loadState(state: State) {
        internalState = state
        // Migrate legacy openaiApiKey/openaiModel into the new fields if blank
        if (internalState.aiApiKey.isBlank() && internalState.openaiApiKey.isNotBlank()) {
            internalState.aiApiKey = internalState.openaiApiKey
        }
        if (internalState.aiModel.isBlank() && internalState.openaiModel.isNotBlank()) {
            internalState.aiModel = internalState.openaiModel
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): QaCopilotProjectSettings =
            project.getService(QaCopilotProjectSettings::class.java)
    }
}

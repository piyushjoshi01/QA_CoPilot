package com.qacopilot.intellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField

/**
 * Settings page: Tools → QA CoPilot
 *
 * Sections:
 *  1. General        — output folder, generator selection
 *  2. AI Provider    — provider dropdown with dynamic fields per provider
 *  3. Requirements   — business requirements document upload + toggle
 *  4. Test Execution — API base URL, auth token, Cucumber tags
 *
 * Provider       | API Key | Model | Base URL | API Version
 * ─────────────────────────────────────────────────────────
 * OpenAI         |   ✓    |   ✓   |    –     |     –
 * Anthropic      |   ✓    |   ✓   |    –     |     –
 * Azure OpenAI   |   ✓    |   ✓   |  ✓ req   |  ✓ req
 * Local LLM      |   –    |   ✓   |  ✓ opt   |     –
 */
class QaCopilotConfigurable(private val project: Project) : Configurable {

    // ── General ───────────────────────────────────────────────────────────────
    private val outputPath = JBTextField()
    private val generatorId = JComboBox(arrayOf("template", "ai"))

    // ── Scenario options ──────────────────────────────────────────────────────
    private val incrementalScenario = JBCheckBox("Incremental scenarios (reuse .qacopilot/scenario-cache.json)", true)
    private val incrementalParse = JBCheckBox("Incremental Java parse (reuse .qacopilot/parse-cache.json)", true)

    // ── AI provider ───────────────────────────────────────────────────────────
    private val aiProvider = JComboBox(arrayOf(
        PROVIDER_OPENAI, PROVIDER_ANTHROPIC, PROVIDER_AZURE, PROVIDER_LOCAL,
    ))
    private val aiApiKey = JPasswordField()
    private val aiModel = JBTextField()
    private val aiBaseUrl = JBTextField()
    private val aiApiVersion = JBTextField()
    private val aiResponseCache = JBCheckBox("Cache AI responses to disk (.qacopilot/ai-response-cache/)", false)
    private val aiFallback = JBCheckBox("Fall back to template scenarios on AI chunk failure", true)

    // Labeled row components — tracked so we can toggle visibility
    private val apiKeyLabel = JBLabel("API key:")
    private val baseUrlLabel = JBLabel("Base URL:")
    private val apiVersionLabel = JBLabel("API version (Azure only):")

    // ── Business requirements ─────────────────────────────────────────────────
    private val requirementsEnabled = JBCheckBox(
        "Enable requirements ingestion — enriches AI prompt with business context", false,
    )
    private val requirementsDocField = TextFieldWithBrowseButton()
    private val requirementsHint = JBLabel(
        "<html>Supported: <b>.txt</b>, <b>.md</b> (built-in) · " +
        "<b>.pdf</b> (add pdfbox to classpath) · <b>.docx</b> (add poi-ooxml to classpath)" +
        "<br>On every sync, AI extracts requirements → generates Gherkin that covers each acceptance criterion.</html>"
    )

    // ── Test execution ────────────────────────────────────────────────────────
    private val apiBase = JBTextField()
    private val authToken = JBTextField()
    private val cucumberTags = JBTextField()

    private val settings get() = QaCopilotProjectSettings.getInstance(project)

    override fun getDisplayName(): String = "QA CoPilot"

    override fun createComponent(): JComponent {
        aiApiKey.columns = 40
        aiModel.columns = 30

        requirementsDocField.addBrowseFolderListener(
            "Select Business Requirements Document",
            "Choose a requirements document (.txt, .md, .pdf, .docx). " +
            "The AI will extract structured requirements and use them to generate richer Gherkin scenarios.",
            project,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter { vf: VirtualFile ->
                    val name = vf.name.lowercase()
                    name.endsWith(".txt") || name.endsWith(".md") ||
                    name.endsWith(".markdown") || name.endsWith(".pdf") || name.endsWith(".docx")
                }
                .withTitle("Select Requirements Document")
                .withDescription("Supported formats: .txt, .md, .pdf, .docx"),
        )

        aiProvider.addItemListener { updateProviderHints() }
        generatorId.addItemListener { updateAiSectionVisibility() }
        requirementsEnabled.addItemListener { updateRequirementsVisibility() }

        populateFrom(settings.state)

        val form = FormBuilder.createFormBuilder()

            // ── General ───────────────────────────────────────────────────────
            .addLabeledComponent("Output folder (relative or absolute):", outputPath, 1, false)
            .addLabeledComponent("Generator:", generatorId, 1, false)
            .addComponent(sectionLabel("Scenario Generation"))
            .addComponent(incrementalScenario)
            .addComponent(incrementalParse)

            // ── AI Provider ───────────────────────────────────────────────────
            .addComponent(sectionLabel("AI Provider"))
            .addLabeledComponent("Provider:", aiProvider, 1, false)
            .addLabeledComponent(apiKeyLabel, aiApiKey, 1, false)
            .addLabeledComponent("Model:", aiModel, 1, false)
            .addLabeledComponent(baseUrlLabel, aiBaseUrl, 1, false)
            .addLabeledComponent(apiVersionLabel, aiApiVersion, 1, false)
            .addComponent(aiResponseCache)
            .addComponent(aiFallback)
            .addComponent(hintLabel(
                "Keys are stored in .idea/workspace.xml (project-local, not committed by default)."
            ))

            // ── Business Requirements ─────────────────────────────────────────
            .addComponent(sectionLabel("Business Requirements"))
            .addComponent(requirementsEnabled)
            .addLabeledComponent("Requirements document:", requirementsDocField, 1, false)
            .addComponent(requirementsHint)
            .addComponent(hintLabel(
                "Output: .qacopilot/requirements/requirements.json · coverage-map.json"
            ))

            // ── Test Execution ────────────────────────────────────────────────
            .addComponent(sectionLabel("Test Execution"))
            .addLabeledComponent("Default API base URL (-Dapi.base.url):", apiBase, 1, false)
            .addLabeledComponent("Default auth token (optional):", authToken, 1, false)
            .addLabeledComponent(
                "Default Cucumber tags (-Dcucumber.filter.tags):", cucumberTags, 1, false,
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel

        form.border = JBUI.Borders.empty(8)

        updateProviderHints()
        updateAiSectionVisibility()
        updateRequirementsVisibility()

        return form
    }

    // ── State sync ────────────────────────────────────────────────────────────

    override fun isModified(): Boolean {
        val s = settings.state
        return outputPath.text != s.outputPath ||
            (generatorId.selectedItem as? String) != s.generatorId ||
            incrementalScenario.isSelected != s.incrementalScenarioMode ||
            incrementalParse.isSelected != s.incrementalParse ||
            providerIdOf(aiProvider.selectedItem as String) != s.aiProvider ||
            String(aiApiKey.password) != s.aiApiKey ||
            aiModel.text != s.aiModel ||
            aiBaseUrl.text != s.aiBaseUrl ||
            aiApiVersion.text != s.aiApiVersion ||
            aiResponseCache.isSelected != s.aiResponseCache ||
            aiFallback.isSelected != s.aiFallbackToTemplate ||
            requirementsEnabled.isSelected != s.requirementsEnabled ||
            requirementsDocField.text != s.requirementsDocPath ||
            apiBase.text != s.defaultApiBaseUrl ||
            authToken.text != s.defaultAuthToken ||
            cucumberTags.text != s.cucumberFilterTags
    }

    override fun apply() {
        val s = settings.state
        s.outputPath = outputPath.text.trim()
        s.generatorId = (generatorId.selectedItem as? String)?.trim()?.ifEmpty { "template" } ?: "template"
        s.incrementalScenarioMode = incrementalScenario.isSelected
        s.incrementalParse = incrementalParse.isSelected
        s.aiProvider = providerIdOf(aiProvider.selectedItem as String)
        s.aiApiKey = String(aiApiKey.password).trim()
        s.aiModel = aiModel.text.trim()
        s.aiBaseUrl = aiBaseUrl.text.trim()
        s.aiApiVersion = aiApiVersion.text.trim()
        s.aiResponseCache = aiResponseCache.isSelected
        s.aiFallbackToTemplate = aiFallback.isSelected
        s.requirementsEnabled = requirementsEnabled.isSelected
        s.requirementsDocPath = requirementsDocField.text.trim()
        s.defaultApiBaseUrl = apiBase.text.trim()
        s.defaultAuthToken = authToken.text.trim()
        s.cucumberFilterTags = cucumberTags.text.trim()
        // Keep legacy openai fields in sync for env-var fallback
        if (s.aiProvider == "openai") {
            s.openaiApiKey = s.aiApiKey
            s.openaiModel = s.aiModel
        }
    }

    override fun reset() {
        populateFrom(settings.state)
        updateProviderHints()
        updateAiSectionVisibility()
        updateRequirementsVisibility()
    }

    // ── Dynamic field visibility ───────────────────────────────────────────────

    private fun updateProviderHints() {
        val provider = aiProvider.selectedItem as? String ?: PROVIDER_OPENAI
        when (provider) {
            PROVIDER_OPENAI -> {
                apiKeyLabel.text = "OpenAI API key (sk-...):"
                aiApiKey.toolTipText = "platform.openai.com/api-keys — env: OPENAI_API_KEY or AI_API_KEY"
                aiModel.toolTipText = "e.g. gpt-4o-mini (default), gpt-4o, gpt-4-turbo, o3-mini"
                setRowVisible(aiBaseUrl, baseUrlLabel, false)
                setRowVisible(aiApiVersion, apiVersionLabel, false)
            }
            PROVIDER_ANTHROPIC -> {
                apiKeyLabel.text = "Anthropic API key (sk-ant-...):"
                aiApiKey.toolTipText = "console.anthropic.com — env: ANTHROPIC_API_KEY or AI_API_KEY"
                aiModel.toolTipText = "e.g. claude-3-5-sonnet-20241022 (default), claude-3-haiku-20240307"
                setRowVisible(aiBaseUrl, baseUrlLabel, false)
                setRowVisible(aiApiVersion, apiVersionLabel, false)
            }
            PROVIDER_AZURE -> {
                apiKeyLabel.text = "Azure OpenAI API key:"
                aiApiKey.toolTipText = "Resource-level key from Azure Portal — env: AI_API_KEY"
                aiModel.toolTipText = "Your deployment name, e.g. gpt-4o — env: AI_MODEL"
                baseUrlLabel.text = "Azure resource URL (required):"
                aiBaseUrl.toolTipText = "https://<resource>.openai.azure.com/openai/deployments/<deployment>"
                setRowVisible(aiBaseUrl, baseUrlLabel, true)
                setRowVisible(aiApiVersion, apiVersionLabel, true)
                if (aiApiVersion.text.isBlank()) aiApiVersion.text = "2024-02-01"
            }
            PROVIDER_LOCAL -> {
                apiKeyLabel.text = "API key (leave blank for Ollama):"
                aiApiKey.toolTipText = "Most local servers don't require a key — env: AI_API_KEY"
                aiModel.toolTipText = "e.g. llama3.2, mistral, phi3, deepseek-r1:7b — env: AI_MODEL"
                baseUrlLabel.text = "Local server base URL:"
                aiBaseUrl.toolTipText = "Default: http://localhost:11434/v1 (Ollama) — env: AI_BASE_URL"
                if (aiBaseUrl.text.isBlank()) aiBaseUrl.text = "http://localhost:11434/v1"
                setRowVisible(aiBaseUrl, baseUrlLabel, true)
                setRowVisible(aiApiVersion, apiVersionLabel, false)
            }
        }
        aiApiKey.parent?.revalidate()
        aiApiKey.parent?.repaint()
    }

    private fun setRowVisible(field: JComponent, label: JComponent, visible: Boolean) {
        field.isVisible = visible
        label.isVisible = visible
    }

    private fun updateAiSectionVisibility() {
        val isAi = generatorId.selectedItem == "ai"
        listOf(aiProvider, aiApiKey, aiModel, aiBaseUrl, aiApiVersion, aiResponseCache, aiFallback,
               apiKeyLabel, baseUrlLabel, apiVersionLabel)
            .forEach { it.isEnabled = isAi }
    }

    private fun updateRequirementsVisibility() {
        val enabled = requirementsEnabled.isSelected
        requirementsDocField.isEnabled = enabled
        requirementsHint.isEnabled = enabled
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun populateFrom(s: QaCopilotProjectSettings.State) {
        outputPath.text = s.outputPath
        generatorId.selectedItem = s.generatorId.ifBlank { "template" }
        incrementalScenario.isSelected = s.incrementalScenarioMode
        incrementalParse.isSelected = s.incrementalParse
        aiProvider.selectedItem = displayNameOf(s.aiProvider)
        aiApiKey.text = s.aiApiKey
        aiModel.text = s.aiModel
        aiBaseUrl.text = s.aiBaseUrl
        aiApiVersion.text = s.aiApiVersion
        aiResponseCache.isSelected = s.aiResponseCache
        aiFallback.isSelected = s.aiFallbackToTemplate
        requirementsEnabled.isSelected = s.requirementsEnabled
        requirementsDocField.text = s.requirementsDocPath
        apiBase.text = s.defaultApiBaseUrl
        authToken.text = s.defaultAuthToken
        cucumberTags.text = s.cucumberFilterTags
    }

    private fun providerIdOf(display: String): String = when (display) {
        PROVIDER_OPENAI -> "openai"
        PROVIDER_ANTHROPIC -> "anthropic"
        PROVIDER_AZURE -> "azure-openai"
        PROVIDER_LOCAL -> "local-llm"
        else -> "openai"
    }

    private fun displayNameOf(id: String): String = when (id) {
        "openai" -> PROVIDER_OPENAI
        "anthropic" -> PROVIDER_ANTHROPIC
        "azure-openai" -> PROVIDER_AZURE
        "local-llm" -> PROVIDER_LOCAL
        else -> PROVIDER_OPENAI
    }

    private fun sectionLabel(text: String): JComponent {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD)
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 0, 2, 0),
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
        )
        panel.add(label, BorderLayout.WEST)
        return panel
    }

    private fun hintLabel(text: String): JComponent {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(label.font.size2D * 0.9f)
        label.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        return label
    }

    companion object {
        private const val PROVIDER_OPENAI = "OpenAI"
        private const val PROVIDER_ANTHROPIC = "Anthropic (Claude)"
        private const val PROVIDER_AZURE = "Azure OpenAI"
        private const val PROVIDER_LOCAL = "Local LLM (Ollama / vLLM)"
    }
}

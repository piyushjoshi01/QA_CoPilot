package com.qacopilot.intellij.ui

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.qacopilot.intellij.QaCopilotGenerationRunner
import com.qacopilot.intellij.run.QaCopilotGradleTestExecution
import com.qacopilot.intellij.settings.QaCopilotProjectSettings
import com.qacopilot.intellij.state.QaCopilotLastRunService
import com.qacopilot.intellij.util.QaCopilotPathUtil
import java.awt.BorderLayout
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextArea

class QaCopilotToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    // ── Status display ────────────────────────────────────────────────────────
    private val outputPathLabel = JBLabel()
    private val lastRunArea = JTextArea(6, 40)

    // ── Test execution ────────────────────────────────────────────────────────
    private val apiBaseField = JBTextField()
    private val tagsField = JBTextField()
    private val dryRunButton = JButton("Run tests (dry run)")
    private val liveRunButton = JButton("Run tests (live API)")

    // ── Requirements quick-pick ───────────────────────────────────────────────
    private val requirementsToggle = JCheckBox("Ingest requirements on next sync")
    private val requirementsDocPicker = TextFieldWithBrowseButton()
    private val requirementsStatus = JBLabel()

    // ── Generate button ───────────────────────────────────────────────────────
    private val generateButton = JButton("⚡ Generate QA Tests")

    init {
        lastRunArea.isEditable = false
        lastRunArea.lineWrap = true
        lastRunArea.wrapStyleWord = true
        lastRunArea.font = lastRunArea.font.deriveFont(Font.PLAIN, 12f)

        // Requirements document picker
        requirementsDocPicker.addBrowseFolderListener(
            "Select Requirements Document",
            "The AI will extract requirements and enrich generated Gherkin scenarios with business context.",
            project,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter { vf: VirtualFile ->
                    val name = vf.name.lowercase()
                    name.endsWith(".txt") || name.endsWith(".md") ||
                    name.endsWith(".markdown") || name.endsWith(".pdf") || name.endsWith(".docx")
                }
                .withTitle("Select Requirements Document (.txt, .md, .pdf, .docx)"),
        )

        // Sync toggle + picker with settings
        requirementsToggle.addItemListener {
            val enabled = requirementsToggle.isSelected
            requirementsDocPicker.isEnabled = enabled
            val st = QaCopilotProjectSettings.getInstance(project).state
            st.requirementsEnabled = enabled
        }

        requirementsDocPicker.addActionListener {
            val st = QaCopilotProjectSettings.getInstance(project).state
            st.requirementsDocPath = requirementsDocPicker.text.trim()
        }

        QaCopilotUiNotifier.register(project, this) { refresh() }

        // ── Layout ────────────────────────────────────────────────────────────
        val statusSection = FormBuilder.createFormBuilder()
            .addLabeledComponent("Output directory:", outputPathLabel, 1, false)
            .addComponent(JBScrollPane(lastRunArea).apply { preferredSize = JBUI.size(400, 130) })
            .panel

        val requirementsSection = FormBuilder.createFormBuilder()
            .addComponent(sectionLabel("Business Requirements"))
            .addComponent(requirementsToggle)
            .addLabeledComponent("Document (.txt/.md/.pdf/.docx):", requirementsDocPicker, 1, false)
            .addComponent(requirementsStatus)
            .panel

        val testSection = FormBuilder.createFormBuilder()
            .addComponent(sectionLabel("Test Execution"))
            .addLabeledComponent("API base URL (-Dapi.base.url):", apiBaseField, 1, false)
            .addLabeledComponent(
                "Cucumber tags (-Dcucumber.filter.tags), e.g. @domain_user:",
                tagsField, 1, false,
            )
            .panel

        val form = JPanel(BorderLayout())
        form.add(statusSection, BorderLayout.NORTH)
        val middle = JPanel()
        middle.layout = javax.swing.BoxLayout(middle, javax.swing.BoxLayout.Y_AXIS)
        middle.add(requirementsSection)
        middle.add(testSection)
        form.add(middle, BorderLayout.CENTER)

        val actions = JPanel().apply {
            add(generateButton)
            add(dryRunButton)
            add(liveRunButton)
        }

        add(form, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
        border = JBUI.Borders.empty(8)

        // ── Action listeners ──────────────────────────────────────────────────
        generateButton.addActionListener {
            // Persist quick-pick values before triggering generation
            syncQuickPickToSettings()
            QaCopilotGenerationRunner.submitGenerate(project)
        }
        dryRunButton.addActionListener { runTests(dryRun = true) }
        liveRunButton.addActionListener { runTests(dryRun = false) }

        refresh()
    }

    // ── Test execution ────────────────────────────────────────────────────────

    private fun runTests(dryRun: Boolean) {
        val root = QaCopilotPathUtil.resolveFrameworkOutput(project) ?: return
        if (!QaCopilotPathUtil.looksLikeGeneratedTestProject(root)) {
            QaCopilotGenerationRunner.notify(
                project,
                "Cannot run tests",
                "Output folder does not look like a generated QA CoPilot project. Generate tests first.",
                NotificationType.WARNING,
            )
            return
        }
        val st = QaCopilotProjectSettings.getInstance(project).state
        val api = apiBaseField.text.trim().ifEmpty { st.defaultApiBaseUrl.ifBlank { "http://localhost:8080" } }
        val tags = tagsField.text.trim()
        st.defaultApiBaseUrl = api
        st.cucumberFilterTags = tags
        QaCopilotGradleTestExecution.runFromToolWindow(project, root, api, tags, dryRun)
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    fun refresh() {
        val resolved = QaCopilotPathUtil.resolveFrameworkOutput(project)
        outputPathLabel.text = resolved?.toString() ?: "(no project base path)"

        val last = QaCopilotLastRunService.getInstance(project).state
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        val time =
            if (last.lastSuccessEpochMillis > 0L) fmt.format(Instant.ofEpochMilli(last.lastSuccessEpochMillis))
            else "—"

        lastRunArea.text = buildString {
            appendLine("Last sync:      $time")
            appendLine("Endpoints:      ${if (last.lastEndpointCount >= 0) last.lastEndpointCount else "—"}")
            appendLine("Generator:      ${last.lastGeneratorId.ifEmpty { "—" }}")
            appendLine("Scenarios:      ${last.lastScenarioModeLabel.ifEmpty { "—" }}")
            if (last.lastRequirementsCount >= 0) {
                appendLine("Requirements:   ${last.lastRequirementsCount} extracted" +
                    (if (last.lastRequirementsFile.isNotBlank()) " from ${last.lastRequirementsFile}" else ""))
            }
            if (last.lastOutputPath.isNotBlank()) {
                appendLine("Output:         ${last.lastOutputPath}")
            }
        }.trimEnd()

        val st = QaCopilotProjectSettings.getInstance(project).state
        if (apiBaseField.text.isBlank()) apiBaseField.text = st.defaultApiBaseUrl
        if (tagsField.text.isBlank()) tagsField.text = st.cucumberFilterTags

        // Sync requirements quick-pick with settings
        requirementsToggle.isSelected = st.requirementsEnabled
        requirementsDocPicker.text = st.requirementsDocPath
        requirementsDocPicker.isEnabled = st.requirementsEnabled
        refreshRequirementsStatus(st, resolved)
    }

    // ── Requirements status line ──────────────────────────────────────────────

    private fun refreshRequirementsStatus(
        st: QaCopilotProjectSettings.State,
        outputRoot: Path?,
    ) {
        val last = QaCopilotLastRunService.getInstance(project).state

        if (!st.requirementsEnabled || st.requirementsDocPath.isBlank()) {
            requirementsStatus.text = "<html><font color='gray'>Requirements ingestion is off. " +
                "Enable it and select a document to enrich AI-generated scenarios with business context.</font></html>"
            requirementsStatus.icon = null
            return
        }

        val docPath = Path.of(st.requirementsDocPath)
        if (!Files.isRegularFile(docPath)) {
            requirementsStatus.text = "<html><font color='#CC0000'>⚠ File not found: ${st.requirementsDocPath}</font></html>"
            return
        }

        // Check if requirements JSON already exists from a previous run
        val reqJson = outputRoot?.resolve(".qacopilot/requirements/requirements.json")

        requirementsStatus.text = when {
            last.lastRequirementsCount > 0 ->
                "<html><font color='#008800'>✓ ${last.lastRequirementsCount} requirements extracted " +
                "from ${last.lastRequirementsFile} — coverage map available</font></html>"
            reqJson != null && Files.isRegularFile(reqJson) ->
                "<html><font color='gray'>✓ Requirements JSON found from previous run. " +
                "Re-sync to refresh.</font></html>"
            else ->
                "<html><font color='gray'>📄 ${docPath.fileName} ready — run sync to extract requirements.</font></html>"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun syncQuickPickToSettings() {
        val st = QaCopilotProjectSettings.getInstance(project).state
        st.requirementsEnabled = requirementsToggle.isSelected
        val picked = requirementsDocPicker.text.trim()
        if (picked.isNotBlank()) {
            st.requirementsDocPath = picked
        }
    }

    private fun sectionLabel(text: String): JPanel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD)
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(8, 0, 2, 0),
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
        )
        panel.add(label, BorderLayout.WEST)
        return panel
    }

    override fun dispose() {}
}

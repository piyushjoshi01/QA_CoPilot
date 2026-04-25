package com.qacopilot.intellij.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class QaCopilotTestRunSettingsEditor : SettingsEditor<QaCopilotTestRunConfiguration>() {

    private val workingDirectory = JBTextField()
    private val apiBaseUrl = JBTextField()
    private val cucumberTags = JBTextField()
    private val dryRun = JBCheckBox("Dry run (no live HTTP; -Dqacopilot.dryRun=true)", false)

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Generated test project directory:", workingDirectory, 1, false)
            .addLabeledComponent("API base URL (-Dapi.base.url):", apiBaseUrl, 1, false)
            .addLabeledComponent(
                "Cucumber tag expression (-Dcucumber.filter.tags), e.g. @domain_user or @smoke and not @wip:",
                cucumberTags,
                1,
                false,
            )
            .addComponent(dryRun)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun resetEditorFrom(s: QaCopilotTestRunConfiguration) {
        workingDirectory.text = s.workingDirectory
        apiBaseUrl.text = s.apiBaseUrl
        cucumberTags.text = s.cucumberTags
        dryRun.isSelected = s.dryRun
    }

    override fun applyEditorTo(s: QaCopilotTestRunConfiguration) {
        s.workingDirectory = workingDirectory.text.trim()
        s.apiBaseUrl = apiBaseUrl.text.trim().ifEmpty { "http://localhost:8080" }
        s.cucumberTags = cucumberTags.text.trim()
        s.dryRun = dryRun.isSelected
    }
}

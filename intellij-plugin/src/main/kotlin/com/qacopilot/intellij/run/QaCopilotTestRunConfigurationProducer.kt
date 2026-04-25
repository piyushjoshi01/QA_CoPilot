package com.qacopilot.intellij.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.qacopilot.intellij.settings.QaCopilotProjectSettings
import com.qacopilot.intellij.util.QaCopilotPathUtil
import java.nio.file.Path

class QaCopilotTestRunConfigurationProducer : LazyRunConfigurationProducer<QaCopilotTestRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        val type = ConfigurationTypeUtil.findConfigurationType(QaCopilotTestRunConfigurationType::class.java)
        return type.configurationFactories[0]
    }

    public override fun setupConfigurationFromContext(
        configuration: QaCopilotTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val project = context.project ?: return false
        val dir = frameworkRootFromContext(context) ?: return false
        if (!QaCopilotPathUtil.looksLikeGeneratedTestProject(dir)) return false
        val st = QaCopilotProjectSettings.getInstance(project).state
        configuration.workingDirectory = dir.toString()
        configuration.apiBaseUrl = st.defaultApiBaseUrl.ifBlank { "http://localhost:8080" }
        configuration.cucumberTags = st.cucumberFilterTags.trim()
        configuration.name = "QA CoPilot (${dir.fileName})"
        return true
    }

    public override fun isConfigurationFromContext(
        configuration: QaCopilotTestRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val dir = frameworkRootFromContext(context) ?: return false
        return configuration.workingDirectory == dir.toString()
    }

    /**
     * Walks up from the context virtual file to find a directory that looks like a generated QA CoPilot
     * Gradle project (gradlew + Cucumber runner).
     */
    private fun frameworkRootFromContext(context: ConfigurationContext): Path? {
        val vf =
            context.location?.virtualFile
                ?: (context.psiLocation as? PsiFile)?.virtualFile
                ?: context.psiLocation?.containingFile?.virtualFile
                ?: return null
        var current = if (vf.isDirectory) vf else vf.parent
        while (current != null) {
            val path = java.nio.file.Path.of(current.path)
            if (QaCopilotPathUtil.looksLikeGeneratedTestProject(path)) {
                return path
            }
            current = current.parent
        }
        return null
    }

    public override fun shouldReplace(
        self: ConfigurationFromContext,
        other: ConfigurationFromContext,
    ): Boolean = false
}

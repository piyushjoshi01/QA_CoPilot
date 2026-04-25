package com.qacopilot.intellij.run

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Starts a [QaCopilotTestRunConfiguration] from the Run window (same as a saved run configuration). */
object QaCopilotGradleTestExecution {

    fun runFromToolWindow(
        project: Project,
        frameworkRoot: Path,
        apiBaseUrl: String,
        cucumberTags: String,
        dryRun: Boolean,
    ) {
        val type = ConfigurationTypeUtil.findConfigurationType(QaCopilotTestRunConfigurationType::class.java)
        val factory = type.configurationFactories[0]
        val configuration = factory.createTemplateConfiguration(project) as QaCopilotTestRunConfiguration
        configuration.name = "QA CoPilot (${frameworkRoot.fileName})"
        configuration.workingDirectory = frameworkRoot.toAbsolutePath().normalize().toString()
        configuration.apiBaseUrl = apiBaseUrl.ifBlank { "http://localhost:8080" }
        configuration.cucumberTags = cucumberTags.trim()
        configuration.dryRun = dryRun
        val settings = RunManager.getInstance(project).createConfiguration(configuration, factory)
        settings.isTemporary = true
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

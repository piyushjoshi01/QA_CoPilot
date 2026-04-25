package com.qacopilot.intellij.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project

class QaCopilotTestRunConfigurationType : ConfigurationType {

    private val factory =
        object : ConfigurationFactory(this) {
            override fun getId(): String = "QaCopilotGradleTestFactory"

            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                QaCopilotTestRunConfiguration(project, this, "QA CoPilot tests")
        }

    override fun getDisplayName(): String = "QA CoPilot tests"

    override fun getConfigurationTypeDescription(): String =
        "Runs ./gradlew test in the generated Gradle output with API base URL and Cucumber tag filters."

    override fun getIcon() = AllIcons.Actions.Execute

    override fun getId(): String = "QaCopilotGradleTest"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

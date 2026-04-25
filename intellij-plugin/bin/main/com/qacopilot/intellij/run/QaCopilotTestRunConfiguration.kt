package com.qacopilot.intellij.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

class QaCopilotTestRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<EmptyQaCopilotRunOptions>(project, factory, name) {

    var workingDirectory: String = ""
    var apiBaseUrl: String = "http://localhost:8080"
    var cucumberTags: String = ""
    var dryRun: Boolean = false

    override fun getConfigurationEditor(): SettingsEditor<out QaCopilotTestRunConfiguration> =
        QaCopilotTestRunSettingsEditor()

    @Throws(RuntimeConfigurationError::class, RuntimeConfigurationWarning::class)
    override fun checkConfiguration() {
        if (workingDirectory.isBlank()) {
            throw RuntimeConfigurationError("Set the generated test project directory (folder containing gradlew).")
        }
        val p = Path.of(workingDirectory)
        if (!Files.isDirectory(p)) {
            throw RuntimeConfigurationError("Not a directory: $workingDirectory")
        }
        val gradlew = p.resolve("gradlew")
        val gradlewBat = p.resolve("gradlew.bat")
        if (!Files.isRegularFile(gradlew) && !Files.isRegularFile(gradlewBat)) {
            throw RuntimeConfigurationWarning("No Gradle wrapper found. Generate QA tests first.")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): CommandLineState {
        val root = Path.of(workingDirectory)
        val cmd: GeneralCommandLine = QaCopilotGradleTestCommandLine.build(root, apiBaseUrl, cucumberTags, dryRun)
        return object : CommandLineState(environment) {
            @Throws(ExecutionException::class)
            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler, environment.project)
                return handler
            }
        }
    }

    public override fun clone(): QaCopilotTestRunConfiguration {
        val copy = super.clone() as QaCopilotTestRunConfiguration
        copy.workingDirectory = workingDirectory
        copy.apiBaseUrl = apiBaseUrl
        copy.cucumberTags = cucumberTags
        copy.dryRun = dryRun
        return copy
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        val c = Element(TAG)
        c.setAttribute("workingDirectory", workingDirectory)
        c.setAttribute("apiBaseUrl", apiBaseUrl)
        c.setAttribute("cucumberTags", cucumberTags)
        c.setAttribute("dryRun", dryRun.toString())
        element.addContent(c)
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super.readExternal(element)
        val c = element.getChild(TAG) ?: return
        workingDirectory = c.getAttributeValue("workingDirectory") ?: ""
        apiBaseUrl = c.getAttributeValue("apiBaseUrl") ?: "http://localhost:8080"
        cucumberTags = c.getAttributeValue("cucumberTags") ?: ""
        dryRun = c.getAttributeValue("dryRun")?.toBooleanStrictOrNull() ?: false
    }

    companion object {
        private const val TAG = "QaCopilotGradleTest"
    }
}

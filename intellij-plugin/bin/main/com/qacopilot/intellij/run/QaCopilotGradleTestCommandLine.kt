package com.qacopilot.intellij.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a [GeneralCommandLine] to run `./gradlew test` in a generated QA CoPilot framework directory,
 * forwarding JVM system properties to Gradle so [build.gradle.kts] can pass them to the test worker.
 */
object QaCopilotGradleTestCommandLine {

    fun build(
        frameworkRoot: Path,
        apiBaseUrl: String,
        cucumberTags: String,
        dryRun: Boolean,
    ): GeneralCommandLine {
        val root = frameworkRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(root)) {
            throw IllegalArgumentException("Not a directory: $root")
        }
        val gradlew = if (SystemInfo.isWindows) root.resolve("gradlew.bat") else root.resolve("gradlew")
        if (!Files.isRegularFile(gradlew)) {
            throw IllegalArgumentException("Gradle wrapper not found under: $root")
        }
        val cmd = GeneralCommandLine()
        cmd.withWorkDirectory(root.toString())
        cmd.withCharset(Charsets.UTF_8)
        cmd.exePath = gradlew.toString()
        cmd.addParameters("-Dapi.base.url=$apiBaseUrl")
        if (cucumberTags.isNotBlank()) {
            cmd.addParameters("-Dcucumber.filter.tags=$cucumberTags")
        }
        if (dryRun) {
            cmd.addParameters("-Dqacopilot.dryRun=true")
        }
        cmd.addParameters("test", "--no-daemon")
        return cmd
    }
}

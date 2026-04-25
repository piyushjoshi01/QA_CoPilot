package com.qacopilot.intellij.util

import com.intellij.openapi.project.Project
import com.qacopilot.intellij.settings.QaCopilotProjectSettings
import java.nio.file.Files
import java.nio.file.Path

object QaCopilotPathUtil {

    fun resolveFrameworkOutput(project: Project): Path? {
        val base = project.basePath ?: return null
        val st = QaCopilotProjectSettings.getInstance(project).state
        val outputRaw = st.outputPath.trim().ifEmpty { "qa-tests" }
        val candidate = Path.of(outputRaw)
        return if (candidate.isAbsolute) candidate.normalize()
        else Path.of(base).resolve(outputRaw).normalize()
    }

    fun looksLikeGeneratedTestProject(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        val gradlew = Files.isRegularFile(dir.resolve("gradlew")) || Files.isRegularFile(dir.resolve("gradlew.bat"))
        val build = Files.isRegularFile(dir.resolve("build.gradle.kts")) || Files.isRegularFile(dir.resolve("build.gradle"))
        val runner = Files.isRegularFile(dir.resolve("src/test/java/com/qacopilot/runner/CucumberTestRunner.java"))
        return gradlew && build && runner
    }
}

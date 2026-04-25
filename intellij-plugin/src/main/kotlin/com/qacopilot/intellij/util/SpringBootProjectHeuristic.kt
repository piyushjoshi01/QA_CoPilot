package com.qacopilot.intellij.util

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

object SpringBootProjectHeuristic {

    @JvmStatic
    fun looksLikeSpringBoot(project: Project): Boolean {
        val base = project.basePath ?: return false
        val root = Path.of(base)
        val candidates = listOf("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle.kts")
        for (name in candidates) {
            val p = root.resolve(name)
            if (!Files.isRegularFile(p)) {
                continue
            }
            val text = try {
                Files.readString(p).lowercase()
            } catch (_: Exception) {
                continue
            }
            if ("spring-boot" in text || "springframework" in text) {
                return true
            }
        }
        return false
    }
}

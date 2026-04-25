package com.qacopilot.intellij.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.qacopilot.analysis.extractor.SpringAnalysisOptions
import java.nio.file.Path

/**
 * Maps IntelliJ module roots and compile outputs into {@link SpringAnalysisOptions} so JavaParser
 * symbol solving matches what the IDE compiles (important on large multi-module Boot apps).
 */
object IntellijAnalysisOptionsFactory {

    @JvmStatic
    fun fromProject(project: Project): SpringAnalysisOptions {
        val base = project.basePath ?: return SpringAnalysisOptions.defaults()
        val b = SpringAnalysisOptions.builder()
        b.addSourceRoot(Path.of(base))
        for (module in ModuleManager.getInstance(project).modules) {
            val rootManager = ModuleRootManager.getInstance(module)
            for (root in rootManager.sourceRoots) {
                nioPath(root)?.let { b.addSourceRoot(it) }
            }
            OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().classesRoots.forEach { vf ->
                val p = nioPath(vf) ?: return@forEach
                if (vf.name.endsWith(".jar", ignoreCase = true)) {
                    b.addJarClasspathEntry(p)
                }
            }
        }
        return b.build()
    }

    private fun nioPath(vf: VirtualFile): Path? {
        if (!vf.isInLocalFileSystem) {
            return null
        }
        return try {
            Path.of(vf.path)
        } catch (_: Exception) {
            null
        }
    }
}

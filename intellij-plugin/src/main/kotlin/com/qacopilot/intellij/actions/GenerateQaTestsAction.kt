package com.qacopilot.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.qacopilot.intellij.QaCopilotGenerationRunner
import com.qacopilot.intellij.util.SpringBootProjectHeuristic

class GenerateQaTestsAction :
    AnAction(
        "Generate QA Tests",
        "Run QA CoPilot: scan Spring controllers and refresh the test framework output folder",
        AllIcons.Actions.Execute,
    ) {

    override fun update(e: AnActionEvent) {
        val p = e.project
        e.presentation.isVisible = true
        e.presentation.isEnabled =
            p != null && !p.isDefault && !p.isDisposed && p.basePath != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!SpringBootProjectHeuristic.looksLikeSpringBoot(project)) {
            QaCopilotGenerationRunner.notify(
                project,
                "Not a Spring Boot project?",
                "QA CoPilot did not find spring-boot / springframework in Gradle or Maven files at the project root. You can still run generation if this is a false negative.",
                NotificationType.WARNING,
            )
        }
        QaCopilotGenerationRunner.submitGenerate(project)
    }
}

package com.qacopilot.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lightweight pub/sub so the tool window (and others) can refresh after a successful generation without
 * tight coupling to the action implementation.
 */
object QaCopilotUiNotifier {

    private val listeners = ConcurrentHashMap<Project, MutableSet<() -> Unit>>()

    fun register(project: Project, parentDisposable: Disposable, listener: () -> Unit) {
        listeners.computeIfAbsent(project) { CopyOnWriteArraySet() }.add(listener)
        Disposer.register(parentDisposable) {
            listeners[project]?.remove(listener)
            if (listeners[project]?.isEmpty() == true) {
                listeners.remove(project)
            }
        }
    }

    fun notifyGenerationFinished(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            listeners[project]?.forEach { it() }
        }
    }
}

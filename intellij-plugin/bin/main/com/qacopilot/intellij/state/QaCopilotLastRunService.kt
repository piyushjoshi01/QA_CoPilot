package com.qacopilot.intellij.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(
    name = "QaCopilotLastRun",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class QaCopilotLastRunService : PersistentStateComponent<QaCopilotLastRunService.State> {

    class State {
        var lastSuccessEpochMillis: Long = 0L
        var lastEndpointCount: Int = -1
        var lastOutputPath: String = ""
        var lastGeneratorId: String = ""
        var lastScenarioModeLabel: String = ""
        /** Number of business requirements extracted in the last sync (-1 = not used). */
        var lastRequirementsCount: Int = -1
        /** File name of the requirements document used in the last sync. */
        var lastRequirementsFile: String = ""
        /** Number of requirements that were mapped to at least one endpoint. */
        var lastRequirementsMapped: Int = 0
    }

    private var internalState = State()

    override fun getState(): State = internalState

    override fun loadState(state: State) {
        internalState = state
    }

    /**
     * Record a successful sync run with requirements data.
     *
     * @param requirementsCount  Total requirements extracted; -1 if requirements ingestion was not used.
     * @param requirementsFile   File name of the document (empty if not used).
     */
    fun recordSuccess(
        endpointCount: Int,
        outputPath: String,
        generatorId: String,
        scenarioModeLabel: String,
        requirementsCount: Int = -1,
        requirementsFile: String = "",
        requirementsMapped: Int = 0,
    ) {
        internalState.lastSuccessEpochMillis = System.currentTimeMillis()
        internalState.lastEndpointCount = endpointCount
        internalState.lastOutputPath = outputPath
        internalState.lastGeneratorId = generatorId
        internalState.lastScenarioModeLabel = scenarioModeLabel
        internalState.lastRequirementsCount = requirementsCount
        internalState.lastRequirementsFile = requirementsFile
        internalState.lastRequirementsMapped = requirementsMapped
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): QaCopilotLastRunService =
            project.getService(QaCopilotLastRunService::class.java)
    }
}

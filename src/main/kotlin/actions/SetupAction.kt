package actions

import config.localAction
import io.github.typesafegithub.workflows.domain.actions.Action

class SetupAction(
    private val actionName: String,
    private val versionKey: String,
    private val version: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction(actionName)
    override fun toYamlArguments(): LinkedHashMap<String, String> = linkedMapOf(
        versionKey to version,
    ).apply {
        fetchDepth?.let { put("fetch-depth", it) }
    }

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

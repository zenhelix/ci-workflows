package dsl

import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder

class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList: List<String>? = null
    private var matrixMap: Map<String, Any>? = null

    operator fun WorkflowInput.invoke(value: String) {
        withMap[name] = value
    }

    fun secrets(map: Map<String, String>) {
        secretsMap.putAll(map)
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: Map<String, Any>) {
        matrixMap = matrix
    }

    internal fun toCustomArguments(): Map<String, Any?> = buildMap {
        val needs = needsList
        if (needs != null) put("needs", if (needs.size == 1) needs.first() else needs)
        if (matrixMap != null) put("strategy", mapOf("matrix" to matrixMap))
        put("uses", workflow.usesString)
        if (withMap.isNotEmpty()) put("with", withMap.toMap())
        if (secretsMap.isNotEmpty()) put("secrets", secretsMap.toMap())
    }
}

fun WorkflowBuilder.reusableWorkflowJob(
    id: String,
    name: String? = null,
    uses: ReusableWorkflow,
    condition: String? = null,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
) {
    val builder = ReusableWorkflowJobBuilder(uses).apply(block)
    job(
        id = id,
        name = name,
        runsOn = RunnerType.UbuntuLatest,
        condition = condition,
        _customArguments = builder.toCustomArguments(),
    ) { noop() }
}

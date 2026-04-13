package dsl

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

data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val condition: String? = null,
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: Map<String, Any>? = null,
)

fun reusableJob(
    id: String,
    uses: ReusableWorkflow,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
): ReusableWorkflowJobDef {
    val builder = ReusableWorkflowJobBuilder(uses).apply(block)
    val args = builder.toCustomArguments()
    @Suppress("UNCHECKED_CAST")
    return ReusableWorkflowJobDef(
        id = id,
        uses = uses,
        needs = (args["needs"] as? List<String>)
            ?: (args["needs"] as? String)?.let { listOf(it) }
            ?: emptyList(),
        with = (args["with"] as? Map<String, String>) ?: emptyMap(),
        secrets = (args["secrets"] as? Map<String, String>) ?: emptyMap(),
        strategy = args["strategy"] as? Map<String, Any>,
    )
}

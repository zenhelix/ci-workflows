package dsl

class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
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

    internal fun build(id: String) = ReusableWorkflowJobDef(
        id = id,
        uses = workflow,
        needs = needsList,
        with = withMap.toMap(),
        secrets = secretsMap.toMap(),
        strategy = matrixMap?.let { mapOf("matrix" to it) },
    )
}

data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: Map<String, Any>? = null,
)

fun reusableJob(
    id: String,
    uses: ReusableWorkflow,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
): ReusableWorkflowJobDef =
    ReusableWorkflowJobBuilder(uses).apply(block).build(id)

package dsl

import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml

abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
    private var matrixDef: MatrixDef? = null

    protected fun set(input: WorkflowInput, value: String) {
        withMap[input.name] = value
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: MatrixDef) {
        matrixDef = matrix
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.ref
        }
    }

    fun passthroughAllSecrets() {
        workflow.secrets.forEach { (name, _) ->
            secretsMap[name] = "\${{ secrets.$name }}"
        }
    }

    internal fun build(id: String): ReusableWorkflowJobDef {
        val missingRequired = workflow.inputs
            .filter { (_, input) -> input.required }
            .keys
            .filter { it !in withMap }
        require(missingRequired.isEmpty()) {
            "Job '$id' using '${workflow.fileName}' is missing required inputs: $missingRequired"
        }

        return ReusableWorkflowJobDef(
            id = id,
            uses = workflow,
            needs = needsList,
            with = withMap.toMap(),
            secrets = secretsMap.toMap(),
            strategy = matrixDef,
        )
    }
}

data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: MatrixDef? = null,
) {
    fun toJobYaml(): JobYaml = JobYaml(
        needs = NeedsYaml.of(needs),
        strategy = strategy?.let { StrategyYaml(matrix = it.entries) },
        uses = uses.usesString,
        with = with.takeIf { it.isNotEmpty() },
        secrets = secrets.takeIf { it.isNotEmpty() },
    )
}

package dsl.builder

import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import dsl.core.MatrixDef
import dsl.core.ReusableWorkflow
import dsl.core.WorkflowInput
import dsl.core.WorkflowSecret
import dsl.core.expr
import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml

open class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
    private var matrixDef: MatrixDef? = null

    fun getInput(input: WorkflowInput): String =
        withMap[input.name] ?: error("Input '${input.name}' has not been set")

    fun setInput(input: WorkflowInput, value: String) {
        withMap[input.name] = value
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: MatrixDef) {
        matrixDef = matrix
    }

    private fun addSecrets(secrets: Iterable<WorkflowSecret>) {
        secrets.forEach { secretsMap[it.name] = it.expr }
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) = addSecrets(secrets.asIterable())

    fun passthroughAllSecrets() = addSecrets(workflow.secretObjects)

    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.expr)
    }

    @PublishedApi
    internal fun build(id: String): ReusableWorkflowJobDef {
        val missingRequired = workflow.requiredInputNames.filter { it !in withMap }
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

class SetupAwareJobBuilder<W>(workflow: W) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder
    where W : ReusableWorkflow, W : SetupCapability {

    override var setupAction by stringInput(workflow.setupAction)
    override var setupParams by stringInput(workflow.setupParams)
}

package dsl

import dsl.core.InputRef
import dsl.core.MatrixDef
import dsl.core.ReusableWorkflow
import dsl.core.WorkflowInput
import dsl.core.WorkflowSecret
import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml
import kotlin.reflect.KProperty

class StringInputProperty(private val input: WorkflowInput) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): String =
        builder.getInput(input)

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: String) {
        builder.setInput(input, value)
    }
}

fun stringInput(input: WorkflowInput) = StringInputProperty(input)

class RefInputProperty(private val input: WorkflowInput) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): InputRef =
        InputRef(builder.getInput(input))

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: InputRef) {
        builder.setInput(input, value)
    }
}

fun refInput(input: WorkflowInput) = RefInputProperty(input)

abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
    private var matrixDef: MatrixDef? = null

    fun getInput(input: WorkflowInput): String =
        withMap[input.name] ?: error("Input '${input.name}' has not been set")

    fun setInput(input: WorkflowInput, value: String) {
        withMap[input.name] = value
    }

    fun setInput(input: WorkflowInput, value: InputRef) {
        withMap[input.name] = value.expression
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: MatrixDef) {
        matrixDef = matrix
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.ref.expression
        }
    }

    fun passthroughAllSecrets() {
        workflow.secrets.forEach { (name, _) ->
            secretsMap[name] = "\${{ secrets.$name }}"
        }
    }

    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.ref)
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

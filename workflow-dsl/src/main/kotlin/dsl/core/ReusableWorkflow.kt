package dsl.core

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.ReusableWorkflowJobDef
import dsl.yaml.InputYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

abstract class ReusableWorkflow(val fileName: String) {
    private val inputRegistry = InputRegistry()
    private val _secrets = linkedMapOf<String, Pair<WorkflowCall.Secret, WorkflowSecret>>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
    ): WorkflowInput = inputRegistry.input(name, description, required)

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        val obj = WorkflowSecret(name)
        _secrets[name] = WorkflowCall.Secret(description, required) to obj
        return obj
    }

    val inputDefs: Map<String, WorkflowInputDef> get() = inputRegistry.inputs
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets.mapValues { it.value.first }
    val secretObjects: List<WorkflowSecret> get() = _secrets.values.map { it.second }
    val requiredInputNames: Set<String> by lazy {
        inputRegistry.inputs.filter { (_, def) -> def.required }.keys
    }

    abstract val usesString: String

    fun toInputsYaml(): Map<String, InputYaml>? =
        dsl.yaml.toInputsYaml(inputRegistry.inputs)

    fun toWorkflowCallTrigger(): WorkflowCall {
        val custom = buildMap<String, Any> {
            put("inputs", inputsAsRawMap())
            val secretsRaw = secretsAsRawMap()
            if (secretsRaw.isNotEmpty()) put("secrets", secretsRaw)
        }
        return WorkflowCall(
            _customArguments = custom,
        )
    }

    protected inline fun <B : ReusableWorkflowJobBuilder> buildJob(
        id: String,
        crossinline builderFactory: () -> B,
        block: B.() -> Unit = {},
    ): ReusableWorkflowJobDef {
        val builder = builderFactory()
        builder.block()
        return builder.build(id)
    }

    context(builder: AdapterWorkflowBuilder)
    fun <B : ReusableWorkflowJobBuilder> job(
        id: String,
        factory: () -> B,
        block: B.() -> Unit = {},
    ) {
        builder.registerJob(buildJob(id, factory, block))
    }

    private fun secretsAsRawMap(): Map<String, Map<String, Any?>> =
        _secrets.mapValues { (_, pair) ->
            mapOf("description" to pair.first.description, "required" to pair.first.required)
        }

    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        inputRegistry.inputs.mapValues { (_, def) ->
            buildMap {
                put("description", def.description)
                put("type", def.type.yamlName())
                put("required", def.required)
                def.default?.let { put("default", it.rawValue) }
            }
        }
}

context(builder: AdapterWorkflowBuilder)
fun ReusableWorkflow.simpleJob(
    id: String,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
) = job(id, { ReusableWorkflowJobBuilder(this@simpleJob) }, block)

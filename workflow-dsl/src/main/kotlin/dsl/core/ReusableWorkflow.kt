package dsl.core

import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.ReusableWorkflowJobDef
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

abstract class ReusableWorkflow(val fileName: String) {
    private val inputRegistry = InputRegistry()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required, type, default)

    protected fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.booleanInput(name, description, required, default)

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        return WorkflowSecret(name)
    }

    val inputs: Map<String, WorkflowCall.Input> get() = inputRegistry.inputs
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets
    val requiredInputNames: Set<String> by lazy {
        inputRegistry.inputs.filter { (_, input) -> input.required }.keys
    }

    abstract val usesString: String

    fun toInputsYaml(): Map<String, InputYaml>? =
        dsl.yaml.toInputsYaml(inputRegistry.inputs, inputRegistry.booleanDefaults)

    fun toSecretsYaml(): Map<String, SecretYaml>? =
        _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
            SecretYaml(description = secret.description, required = secret.required)
        }

    fun toWorkflowCallTrigger(): WorkflowCall {
        val secretsMap = _secrets.takeIf { it.isNotEmpty() }?.toMap()
        return if (inputRegistry.booleanDefaults.isEmpty()) {
            WorkflowCall(inputs = inputRegistry.inputs.toMap(), secrets = secretsMap)
        } else {
            WorkflowCall(
                secrets = secretsMap,
                _customArguments = mapOf("inputs" to inputsAsRawMap()),
            )
        }
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

    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        inputRegistry.inputs.mapValues { (name, input) ->
            buildMap {
                put("description", input.description)
                put("type", input.type.name.lowercase())
                put("required", input.required)
                inputRegistry.booleanDefaults[name]?.let { put("default", it) }
                    ?: input.default?.let { put("default", it) }
            }
        }
}

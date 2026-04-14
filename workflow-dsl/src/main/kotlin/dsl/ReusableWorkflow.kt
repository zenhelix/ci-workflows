package dsl

import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

@JvmInline
value class InputRef(val expression: String)

@JvmInline
value class SecretRef(val expression: String)

abstract class ReusableWorkflow(val fileName: String) {
    private val _inputs = mutableMapOf<String, WorkflowCall.Input>()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()
    private val _booleanDefaults = mutableMapOf<String, Boolean>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    protected fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, WorkflowCall.Type.Boolean, null)
        if (default != null) _booleanDefaults[name] = default
        return WorkflowInput(name)
    }

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        return WorkflowSecret(name)
    }

    val inputs: Map<String, WorkflowCall.Input> get() = _inputs.toMap()
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets.toMap()
    val requiredInputNames: Set<String> by lazy {
        _inputs.filter { (_, input) -> input.required }.keys
    }

    abstract val usesString: String

    fun toInputsYaml(): Map<String, InputYaml>? =
        toInputsYaml(_inputs, _booleanDefaults)

    fun toSecretsYaml(): Map<String, SecretYaml>? =
        _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
            SecretYaml(description = secret.description, required = secret.required)
        }

    fun toWorkflowCallTrigger(): WorkflowCall {
        val secretsMap = _secrets.takeIf { it.isNotEmpty() }?.toMap()
        return if (_booleanDefaults.isEmpty()) {
            WorkflowCall(inputs = _inputs.toMap(), secrets = secretsMap)
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
        _inputs.mapValues { (name, input) ->
            buildMap<String, Any?> {
                put("description", input.description)
                put("type", input.type.name.lowercase())
                put("required", input.required)
                val boolDefault = _booleanDefaults[name]
                if (boolDefault != null) {
                    put("default", boolDefault)
                } else if (input.default != null) {
                    put("default", input.default)
                }
            }
        }
}

class WorkflowInput(val name: String) {
    val ref: InputRef = InputRef("\${{ inputs.$name }}")
}

class WorkflowSecret(val name: String) {
    val ref: SecretRef = SecretRef("\${{ secrets.$name }}")
}

inline fun <B : ReusableWorkflowJobBuilder> reusableJob(
    id: String,
    uses: ReusableWorkflow,
    builderFactory: () -> B,
    block: B.() -> Unit = {},
): ReusableWorkflowJobDef {
    val builder = builderFactory()
    builder.block()
    return builder.build(id)
}

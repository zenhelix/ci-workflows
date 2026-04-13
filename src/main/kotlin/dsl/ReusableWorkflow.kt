package dsl

import config.reusableWorkflow
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

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
    val usesString: String get() = reusableWorkflow(fileName)

    /**
     * Creates a WorkflowCall trigger from this workflow's inputs and secrets.
     * Handles boolean defaults correctly by falling back to _customArguments
     * when boolean inputs with defaults exist (WorkflowCall.Input only supports String? defaults).
     */
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

    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        _inputs.map { (name, input) ->
            name to buildMap<String, Any?> {
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
        }.toMap()
}

class WorkflowInput(val name: String)

class WorkflowSecret(val name: String)

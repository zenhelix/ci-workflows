package dsl

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowCall.Input>()
    private val _booleanDefaults = mutableMapOf<String, Boolean>()

    val inputs: Map<String, WorkflowCall.Input> get() = _inputs
    val booleanDefaults: Map<String, Boolean> get() = _booleanDefaults

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, WorkflowCall.Type.Boolean, null)
        default?.let { _booleanDefaults[name] = it }
        return WorkflowInput(name)
    }
}

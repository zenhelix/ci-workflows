package dsl.core

class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowInputDef>()

    val inputs: Map<String, WorkflowInputDef> get() = _inputs

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = InputType.Text,
            required = required,
        )
        return WorkflowInput(name)
    }

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = InputType.Text,
            required = required,
            default = InputDefault.StringDefault(default),
        )
        return WorkflowInput(name)
    }

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = InputType.Boolean,
            required = required,
            default = InputDefault.BooleanDefault(default),
        )
        return WorkflowInput(name)
    }
}

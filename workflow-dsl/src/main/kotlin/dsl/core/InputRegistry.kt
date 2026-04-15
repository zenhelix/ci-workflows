package dsl.core

class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowInputDef>()

    val inputs: Map<String, WorkflowInputDef> get() = _inputs

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: InputDefault? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = if (default is InputDefault.BooleanDefault) InputType.Boolean else InputType.Text,
            required = required,
            default = default,
        )
        return WorkflowInput(name)
    }
}

fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: String): WorkflowInput =
    input(name, description, required, InputDefault(default))

fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: Boolean): WorkflowInput =
    input(name, description, required, InputDefault(default))

package dsl.core

@JvmInline
value class InputRef(val expression: String)

@JvmInline
value class SecretRef(val expression: String)

class WorkflowInput(val name: String) {
    val ref: InputRef = InputRef("\${{ inputs.$name }}")
}

class WorkflowSecret(val name: String) {
    val ref: SecretRef = SecretRef("\${{ secrets.$name }}")
}

sealed interface InputDefault {
    val rawValue: Any

    data class StringDefault(val value: String) : InputDefault {
        override val rawValue get() = value
    }
    data class BooleanDefault(val value: Boolean) : InputDefault {
        override val rawValue get() = value
    }
}

enum class InputType {
    Text, Boolean, Number, Choice;

    fun yamlName(): String = when (this) {
        Text -> "string"
        else -> name.lowercase()
    }
}

data class WorkflowInputDef(
    val name: String,
    val description: String,
    val type: InputType = InputType.Text,
    val required: Boolean = false,
    val default: InputDefault? = null,
)

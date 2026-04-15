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
    data class StringDefault(val value: String) : InputDefault
    data class BooleanDefault(val value: Boolean) : InputDefault
}

enum class InputType {
    String, Boolean, Number, Choice;

    fun yamlName(): kotlin.String = name.lowercase()
}

data class WorkflowInputDef(
    val name: kotlin.String,
    val description: kotlin.String,
    val type: InputType = InputType.String,
    val required: kotlin.Boolean = false,
    val default: InputDefault? = null,
)

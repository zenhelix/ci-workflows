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

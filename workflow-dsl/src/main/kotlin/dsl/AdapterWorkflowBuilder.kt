package dsl

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputRegistry = InputRegistry()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.booleanInput(name, description, required, default)

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputRegistry.inputs, inputRegistry.booleanDefaults),
        jobs = jobs.toList(),
    )
}

fun adapterWorkflow(
    fileName: String,
    name: String,
    block: AdapterWorkflowBuilder.() -> Unit,
): AdapterWorkflow {
    val builder = AdapterWorkflowBuilder(fileName, name)
    builder.block()
    return builder.build()
}

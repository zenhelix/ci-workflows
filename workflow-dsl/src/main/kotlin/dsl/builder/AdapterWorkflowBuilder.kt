package dsl.builder

import dsl.core.InputRegistry
import dsl.core.MatrixDef
import dsl.core.MatrixRef
import dsl.core.WorkflowInput
import dsl.yaml.toInputsYaml

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputRegistry = InputRegistry()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
    ): WorkflowInput = inputRegistry.input(name, description, required = required)

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputRegistry.inputs),
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

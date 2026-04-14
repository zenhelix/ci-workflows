package dsl

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputs = linkedMapOf<String, WorkflowCall.Input>()
    private val booleanDefaults = mutableMapOf<String, Boolean>()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(
        name: String,
        description: String,
        default: Boolean? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, false, WorkflowCall.Type.Boolean, null)
        default?.let { booleanDefaults[name] = it }
        return WorkflowInput(name)
    }

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputs, booleanDefaults),
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

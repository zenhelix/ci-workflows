package dsl.capability

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.core.ReusableWorkflow
import dsl.core.WorkflowInput

interface SetupCapability {
    val setupAction: WorkflowInput
    val setupParams: WorkflowInput

    companion object {
        const val SETUP_ACTION_DESCRIPTION = "Setup action to use: gradle, go, python"
        const val SETUP_PARAMS_DESCRIPTION = """JSON object with setup parameters (e.g. {"java-version": "21"})"""
        const val SETUP_PARAMS_DEFAULT = "{}"
    }
}

interface SetupCapableJobBuilder {
    var setupAction: String
    var setupParams: String

    fun applySetup(action: String, params: String) {
        setupAction = action
        setupParams = params
    }
}

context(builder: AdapterWorkflowBuilder)
inline fun <reified W> W.setupJob(
    id: String,
    noinline block: SetupAwareJobBuilder<W>.() -> Unit = {},
) where W : ReusableWorkflow, W : SetupCapability =
    job(id, { SetupAwareJobBuilder(this@setupJob) }, block)

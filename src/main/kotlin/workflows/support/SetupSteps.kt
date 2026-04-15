package workflows.support

import actions.SetupAction
import config.SetupTool
import dsl.capability.SetupCapableJobBuilder
import dsl.core.MatrixRefExpr
import dsl.core.WorkflowInput
import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(
    tools: List<SetupTool> = SetupTool.entries,
    fetchDepth: String? = null,
) {
    tools.forEach { tool ->
        uses(
            name = "Setup ${tool.id.replaceFirstChar { c -> c.uppercase() }}",
            action = SetupAction(
                tool.actionName, tool.versionKey,
                "\${{ fromJson(inputs.setup-params).${tool.versionKey} || '${tool.defaultVersion}' }}",
                fetchDepth,
            ),
            condition = "inputs.setup-action == '${tool.id}'",
        )
    }
}

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    applySetup(tool.id, tool.toParamsJson(versionExpr.expression))
}

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionRef: String) {
    applySetup(tool.id, tool.toParamsJson(versionRef))
}

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionInput: WorkflowInput) {
    applySetup(tool.id, tool.toParamsJson(versionInput.ref.expression))
}

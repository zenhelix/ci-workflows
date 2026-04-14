package workflows.helpers

import actions.SetupAction
import config.SetupTool
import dsl.MatrixRefExpr
import dsl.SetupConfigurable
import dsl.WorkflowInput
import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    listOf(SetupTool.Gradle, SetupTool.Go, SetupTool.Python).forEach { tool ->
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

fun SetupConfigurable.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}

fun SetupConfigurable.setup(tool: SetupTool, versionRef: String) {
    applySetup(tool.id, tool.toParamsJson(versionRef))
}

fun SetupConfigurable.setup(tool: SetupTool, versionInput: WorkflowInput) {
    applySetup(tool.id, tool.toParamsJson(versionInput.ref.expression))
}

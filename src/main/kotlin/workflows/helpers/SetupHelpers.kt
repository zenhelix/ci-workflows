package workflows.helpers

import actions.SetupAction
import config.SetupTool
import dsl.MatrixRefExpr
import dsl.SetupConfigurable
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
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionExpr)
}

fun SetupConfigurable.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

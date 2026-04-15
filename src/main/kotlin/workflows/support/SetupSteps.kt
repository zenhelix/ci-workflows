package workflows.support

import actions.SetupAction
import config.SetupTool
import dsl.capability.SetupCapableJobBuilder
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

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: String) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}

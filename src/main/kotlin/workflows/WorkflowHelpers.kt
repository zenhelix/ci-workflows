package workflows

import actions.SetupAction
import config.SetupTool
import dsl.MatrixRefExpr
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

fun CheckWorkflow.JobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionExpr)
}

fun CheckWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun CreateTagWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun ManualCreateTagWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun PublishWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

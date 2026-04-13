package dsl

import actions.SetupAction
import config.SetupTool
import io.github.typesafegithub.workflows.dsl.JobBuilder

/** Reference an input of the current workflow: generates "\${{ inputs.<name> }}" */
fun inputRef(name: String) = "\${{ inputs.$name }}"

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


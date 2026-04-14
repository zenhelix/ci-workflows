package workflows.adapters.tag

import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.ManualCreateTagWorkflow
import workflows.setup

fun toolManualCreateTag(
    fileName: String,
    name: String,
    tool: SetupTool,
    commandInputName: String,
    commandDescription: String,
    defaultCommand: String,
    defaultTagPrefix: String,
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val version = input(tool.versionKey, description = tool.versionDescription, default = tool.defaultVersion)
    val checkCommand = input(commandInputName, description = commandDescription, default = defaultCommand)
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)

    ManualCreateTagWorkflow.job("manual-tag") {
        ManualCreateTagWorkflow.tagVersion from tagVersion
        ManualCreateTagWorkflow.tagPrefix from tagPrefix
        setup(tool, version.ref.expression)
        ManualCreateTagWorkflow.checkCommand from checkCommand
        passthroughAllSecrets()
    }
}

package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.CreateTagWorkflow
import workflows.setup

fun toolCreateTag(
    fileName: String,
    name: String,
    tool: SetupTool,
    commandInputName: String,
    commandDescription: String,
    defaultCommand: String,
    defaultTagPrefix: String,
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val version = input(tool.versionKey, description = tool.versionDescription, default = tool.defaultVersion)
    val checkCommand = input(commandInputName, description = commandDescription, default = defaultCommand)
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    CreateTagWorkflow.job("create-tag") {
        setup(tool, version.ref.expression)
        CreateTagWorkflow.checkCommand from checkCommand
        CreateTagWorkflow.defaultBump from defaultBump
        CreateTagWorkflow.tagPrefix from tagPrefix
        CreateTagWorkflow.releaseBranches from releaseBranches
        passthroughAllSecrets()
    }
}

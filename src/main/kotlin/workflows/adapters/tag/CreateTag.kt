package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.ToolTagConfig
import dsl.AdapterWorkflow
import dsl.adapterWorkflow
import workflows.CreateTagWorkflow
import workflows.helpers.setup

fun toolCreateTag(
    fileName: String,
    name: String,
    config: ToolTagConfig,
): AdapterWorkflow = adapterWorkflow(fileName, name) {
    val version = input(config.tool.versionKey, description = config.tool.versionDescription, default = config.tool.defaultVersion)
    val checkCommand = input(config.commandInputName, description = config.commandDescription, default = config.defaultCommand)
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = config.defaultTagPrefix)
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    CreateTagWorkflow.job("create-tag") {
        setup(config.tool, version)
        CreateTagWorkflow.checkCommand from checkCommand
        CreateTagWorkflow.defaultBump from defaultBump
        CreateTagWorkflow.tagPrefix from tagPrefix
        CreateTagWorkflow.releaseBranches from releaseBranches
        passthroughAllSecrets()
    }
}

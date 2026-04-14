package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.GO_TAG
import config.GRADLE_TAG
import config.ToolTagConfig
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.CreateTagWorkflow
import workflows.support.setup

object CreateTagAdapters {
    val gradle = toolCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE_TAG)
    val go = toolCreateTag("go-create-tag.yml", "Go Create Tag", GO_TAG)

    private fun toolCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
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
}

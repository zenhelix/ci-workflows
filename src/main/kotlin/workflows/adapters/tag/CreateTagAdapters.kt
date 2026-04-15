package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import dsl.core.expr
import dsl.capability.setupJob
import workflows.base.CreateTagWorkflow
import workflows.support.setup

object CreateTagAdapters {
    val gradle = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val go = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)
}

fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
    adapterWorkflow(fileName, name) {
        val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
        val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
        val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
        val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)
        val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

        CreateTagWorkflow.setupJob("create-tag") {
            setup(eco.tool, version.expr)
            CreateTagWorkflow.checkCommand from checkCommand
            CreateTagWorkflow.defaultBump from defaultBump
            CreateTagWorkflow.tagPrefix from tagPrefix
            CreateTagWorkflow.releaseBranches from releaseBranches
            passthroughAllSecrets()
        }
    }

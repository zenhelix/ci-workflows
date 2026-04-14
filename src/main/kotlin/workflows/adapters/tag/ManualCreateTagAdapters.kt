package workflows.adapters.tag

import config.GO_TAG
import config.GRADLE_TAG
import config.ToolTagConfig
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object ManualCreateTagAdapters {
    val gradle = toolManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE_TAG)
    val go = toolManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO_TAG)

    private fun toolManualCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
            val version = input(config.tool.versionKey, description = config.tool.versionDescription, default = config.tool.defaultVersion)
            val checkCommand = input(config.commandInputName, description = config.commandDescription, default = config.defaultCommand)
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = config.defaultTagPrefix)

            ManualCreateTagWorkflow.job("manual-tag") {
                ManualCreateTagWorkflow.tagVersion from tagVersion
                ManualCreateTagWorkflow.tagPrefix from tagPrefix
                setup(config.tool, version)
                ManualCreateTagWorkflow.checkCommand from checkCommand
                passthroughAllSecrets()
            }
        }
}

package workflows.adapters.tag

import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object ManualCreateTagAdapters {
    val gradle = ecosystemManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE)
    val go = ecosystemManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO)
}

fun ecosystemManualCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
    adapterWorkflow(fileName, name) {
        val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
        val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
        val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
        val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)

        ManualCreateTagWorkflow.job("manual-tag") {
            ManualCreateTagWorkflow.tagVersion from tagVersion
            ManualCreateTagWorkflow.tagPrefix from tagPrefix
            setup(eco.tool, version.ref.expression)
            ManualCreateTagWorkflow.checkCommand from checkCommand
            passthroughAllSecrets()
        }
    }

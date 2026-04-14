package workflows.adapters.tag

import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.definitions.ManualCreateTagWorkflow
import workflows.setup

class ManualCreateTagAdapter(
    fileName: String,
    override val workflowName: String,
    private val tool: SetupTool,
    private val defaultVersion: String,
    private val commandInputName: String,
    private val defaultCommand: String,
    private val defaultTagPrefix: String,
) : ProjectAdapterWorkflow(fileName) {

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val version = input("${tool.versionKey}", description = "${tool.id.replaceFirstChar { it.uppercase() }} version to use", default = defaultVersion)
    val checkCommand = input(commandInputName, description = "Validation command to run before tagging", default = defaultCommand)
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@ManualCreateTagAdapter.tagVersion.ref
            tagPrefix = this@ManualCreateTagAdapter.tagPrefix.ref
            setup(tool, this@ManualCreateTagAdapter.version.ref.expression)
            checkCommand = this@ManualCreateTagAdapter.checkCommand.ref
            passthroughAllSecrets()
        },
    )
}

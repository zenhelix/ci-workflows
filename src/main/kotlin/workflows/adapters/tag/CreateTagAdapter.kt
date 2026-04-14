package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.definitions.CreateTagWorkflow
import workflows.setup

class CreateTagAdapter(
    fileName: String,
    override val workflowName: String,
    private val tool: SetupTool,
    private val defaultVersion: String,
    private val versionDescription: String,
    private val commandInputName: String,
    private val commandDescription: String,
    private val defaultCommand: String,
    private val defaultTagPrefix: String,
) : ProjectAdapterWorkflow(fileName) {

    val version = input(tool.versionKey, description = versionDescription, default = defaultVersion)
    val checkCommand = input(commandInputName, description = commandDescription, default = defaultCommand)
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(tool, version.ref.expression)
            checkCommand = this@CreateTagAdapter.checkCommand.ref
            defaultBump = this@CreateTagAdapter.defaultBump.ref
            tagPrefix = this@CreateTagAdapter.tagPrefix.ref
            releaseBranches = this@CreateTagAdapter.releaseBranches.ref
            passthroughAllSecrets()
        },
    )
}

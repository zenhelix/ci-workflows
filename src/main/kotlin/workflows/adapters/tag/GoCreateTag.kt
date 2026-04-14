package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoCreateTagAdapter : AdapterWorkflow("go-create-tag.yml") {
    override val workflowName = "Go Create Tag"

    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<CreateTagWorkflow.JobBuilder>(id = "create-tag", uses = CreateTagWorkflow) {
            setupAction(SetupTool.Go.id)
            setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            checkCommand(this@GoCreateTagAdapter.checkCommand.ref)
            defaultBump(this@GoCreateTagAdapter.defaultBump.ref)
            tagPrefix(this@GoCreateTagAdapter.tagPrefix.ref)
            releaseBranches(this@GoCreateTagAdapter.releaseBranches.ref)
            passthroughAllSecrets()
        },
    )
}

package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.passthrough
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

    init { secrets(APP_SECRETS) }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction(SetupTool.Go.id)
            CreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            CreateTagWorkflow.checkCommand(checkCommand.ref)
            CreateTagWorkflow.defaultBump(defaultBump.ref)
            CreateTagWorkflow.tagPrefix(tagPrefix.ref)
            CreateTagWorkflow.releaseBranches(releaseBranches.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}

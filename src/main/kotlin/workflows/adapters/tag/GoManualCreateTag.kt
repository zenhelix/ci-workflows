package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.definitions.ManualCreateTagWorkflow
import workflows.ProjectAdapterWorkflow
import workflows.setup

object GoManualCreateTagAdapter : ProjectAdapterWorkflow("go-manual-create-tag.yml") {
    override val workflowName = "Go Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@GoManualCreateTagAdapter.tagVersion.ref
            tagPrefix = this@GoManualCreateTagAdapter.tagPrefix.ref
            setup(SetupTool.Go, goVersion.ref.expression)
            checkCommand = this@GoManualCreateTagAdapter.checkCommand.ref
            passthroughAllSecrets()
        },
    )
}

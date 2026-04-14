package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoManualCreateTagAdapter : AdapterWorkflow("go-manual-create-tag.yml") {
    override val workflowName = "Go Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ManualCreateTagWorkflow.JobBuilder>(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            tagVersion(this@GoManualCreateTagAdapter.tagVersion.ref)
            tagPrefix(this@GoManualCreateTagAdapter.tagPrefix.ref)
            setupAction(SetupTool.Go.id)
            setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            checkCommand(this@GoManualCreateTagAdapter.checkCommand.ref)
            passthroughAllSecrets()
        },
    )
}

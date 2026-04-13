package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_GO_VERSION
import config.SetupTool
import config.passthrough
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

    init { secrets(APP_SECRETS) }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(tagVersion.ref)
            ManualCreateTagWorkflow.tagPrefix(tagPrefix.ref)
            ManualCreateTagWorkflow.setupAction(SetupTool.Go.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            ManualCreateTagWorkflow.checkCommand(checkCommand.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}

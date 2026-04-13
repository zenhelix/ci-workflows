package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.ManualCreateTagWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGoManualCreateTag(outputDir: File) {
    generateAdapterWorkflow(
        name = "Go Manual Create Tag",
        sourceFileSlug = "go-manual-create-tag",
        targetFileName = "go-manual-create-tag.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.tagVersion(),
                CommonInputs.goVersion(),
                CommonInputs.checkCommand(description = "Go validation command", default = "make test"),
                CommonInputs.tagPrefix(default = "v"),
            ),
            secrets = APP_SECRETS,
        ),
        jobs = listOf(
            reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
                ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
                ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
                ManualCreateTagWorkflow.setupAction(SetupTool.Go.id)
                ManualCreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(inputRef("go-version")))
                ManualCreateTagWorkflow.checkCommand(inputRef("check-command"))
                secrets(APP_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

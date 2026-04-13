package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.CreateTagWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGoCreateTag(outputDir: File) {
    generateAdapterWorkflow(
        name = "Go Create Tag",
        sourceFileSlug = "go-create-tag",
        targetFileName = "go-create-tag.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.goVersion(),
                CommonInputs.checkCommand(description = "Go validation command", default = "make test"),
                CommonInputs.defaultBump(),
                CommonInputs.tagPrefix(default = "v"),
                CommonInputs.releaseBranches(),
            ),
            secrets = APP_SECRETS,
        ),
        jobs = listOf(
            reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
                CreateTagWorkflow.setupAction(SetupTool.Go.id)
                CreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(inputRef("go-version")))
                CreateTagWorkflow.checkCommand(inputRef("check-command"))
                CreateTagWorkflow.defaultBump(inputRef("default-bump"))
                CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
                CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
                secrets(APP_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

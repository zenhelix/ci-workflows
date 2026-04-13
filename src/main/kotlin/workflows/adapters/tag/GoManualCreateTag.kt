package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.ManualCreateTagWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.inputRef
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateGoManualCreateTag(outputDir: File) {
    val targetFile = "go-manual-create-tag.yml"

    workflow(
        name = "Go Manual Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    CommonInputs.tagVersion(),
                    CommonInputs.goVersion(),
                    CommonInputs.checkCommand(description = "Go validation command", default = "make test"),
                    CommonInputs.tagPrefix(default = "v"),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/go-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
            ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
            ManualCreateTagWorkflow.setupAction(SetupTool.Go.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(inputRef("go-version")))
            ManualCreateTagWorkflow.checkCommand(inputRef("check-command"))
            secrets(APP_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

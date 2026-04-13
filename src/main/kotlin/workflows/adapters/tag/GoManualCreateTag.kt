package workflows.adapters.tag

import config.APP_SECRETS
import config.passthrough
import config.DEFAULT_GO_VERSION
import dsl.ManualCreateTagWorkflow
import dsl.cleanReusableWorkflowJobs
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
                    "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String),
                    "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, DEFAULT_GO_VERSION),
                    "check-command" to WorkflowCall.Input("Go validation command", false, WorkflowCall.Type.String, "make test"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, "v"),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/go-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion("\${{ inputs.tag-version }}")
            ManualCreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            ManualCreateTagWorkflow.setupAction("go")
            ManualCreateTagWorkflow.setupParams("{\"go-version\": \"\${{ inputs.go-version }}\"}")
            ManualCreateTagWorkflow.checkCommand("\${{ inputs.check-command }}")
            secrets(APP_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

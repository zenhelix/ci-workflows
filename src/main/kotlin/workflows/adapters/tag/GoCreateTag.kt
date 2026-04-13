package workflows.adapters.tag

import config.APP_SECRETS
import config.passthrough
import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import dsl.CreateTagWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateGoCreateTag(outputDir: File) {
    val targetFile = "go-create-tag.yml"

    workflow(
        name = "Go Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, DEFAULT_GO_VERSION),
                    "check-command" to WorkflowCall.Input("Go validation command", false, WorkflowCall.Type.String, "make test"),
                    "default-bump" to WorkflowCall.Input("Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, "v"),
                    "release-branches" to WorkflowCall.Input("Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/go-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction("go")
            CreateTagWorkflow.setupParams("{\"go-version\": \"\${{ inputs.go-version }}\"}")
            CreateTagWorkflow.checkCommand("\${{ inputs.check-command }}")
            CreateTagWorkflow.defaultBump("\${{ inputs.default-bump }}")
            CreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            CreateTagWorkflow.releaseBranches("\${{ inputs.release-branches }}")
            secrets(APP_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

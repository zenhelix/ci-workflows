package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_CHANGELOG_CONFIG
import shared.cleanReusableWorkflowJobs
import shared.dsl.ReleaseWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateAppRelease(outputDir: File) {
    val targetFile = "app-release.yml"

    workflow(
        name = "Application Release",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                "draft" to WorkflowCall.Input("Create release as draft (default true for apps)", false, WorkflowCall.Type.Boolean, "true"),
            )),
        ),
        sourceFile = File(".github/workflow-src/app-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
            ReleaseWorkflow.draft("\${{ inputs.draft }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
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
            WorkflowCall(
                _customArguments = mapOf(
                    "inputs" to mapOf(
                        "changelog-config" to mapOf(
                            "description" to "Path to changelog configuration file",
                            "type" to "string",
                            "required" to false,
                            "default" to DEFAULT_CHANGELOG_CONFIG,
                        ),
                        "draft" to mapOf(
                            "description" to "Create release as draft (default true for apps)",
                            "type" to "boolean",
                            "required" to false,
                            "default" to true,
                        ),
                    ),
                ),
            ),
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

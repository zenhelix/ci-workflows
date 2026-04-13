package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.ReleaseWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateAppRelease(outputDir: File) {
    generateAdapterWorkflow(
        name = "Application Release",
        sourceFileSlug = "app-release",
        targetFileName = "app-release.yml",
        trigger = WorkflowCall(
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
        jobs = listOf(
            reusableJob(id = "release", uses = ReleaseWorkflow) {
                ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
                ReleaseWorkflow.draft(inputRef("draft"))
            },
        ),
        outputDir = outputDir,
    )
}

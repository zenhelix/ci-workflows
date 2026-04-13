package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateRelease(outputDir: File) {
    workflow(
        name = "Release",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/release.main.kts"),
        targetFileName = "release.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "changelog-config" to stringInput(
                            description = "Path to changelog configuration file",
                            default = DEFAULT_CHANGELOG_CONFIG,
                        ),
                        "draft" to booleanInput(
                            description = "Create release as draft",
                            default = false,
                        ),
                    ),
                ),
            ),
            "permissions" to mapOf("contents" to "write"),
        ),
    ) {
        job(
            id = "release",
            name = "GitHub Release",
            runsOn = UbuntuLatest,
        ) {
            uses(
                name = "Check out",
                action = CheckoutAction(fetchDepth = "0"),
            )
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction(
                    configuration = "\${{ inputs.changelog-config }}",
                    toTag = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
            uses(
                name = "Create Release",
                action = GhReleaseAction(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft = "\${{ inputs.draft }}",
                ),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
        }
    }
}

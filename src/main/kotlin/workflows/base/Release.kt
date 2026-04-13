package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import actions.CheckoutAction
import actions.GhReleaseAction
import actions.ReleaseChangelogBuilderAction
import dsl.ReleaseWorkflow
import java.io.File

fun generateRelease() {
    workflow(
        name = "Release",
        on = listOf(
            ReleaseWorkflow.toWorkflowCallTrigger(),
        ),
        sourceFile = File(".github/workflow-src/release.main.kts"),
        targetFileName = "release.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
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

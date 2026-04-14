package workflows.base

import workflows.ReleaseWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
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
                action = Checkout(fetchDepth = Checkout.FetchDepth.Value(0)),
            )
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction_Untyped(
                    configuration_Untyped = ReleaseWorkflow.changelogConfig.ref.expression,
                    toTag_Untyped = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
            uses(
                name = "Create Release",
                action = ActionGhRelease(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft_Untyped = ReleaseWorkflow.draft.ref.expression,
                ),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
        }
    }
}

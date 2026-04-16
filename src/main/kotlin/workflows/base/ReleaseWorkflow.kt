package workflows.base

import config.DEFAULT_CHANGELOG_CONFIG
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import dsl.core.expr
import workflows.ProjectWorkflow

object ReleaseWorkflow : ProjectWorkflow(
    "release.yml", "Release",
    permissions = mapOf(Permission.Contents to Mode.Write),
) {
    val changelogConfig = input("changelog-config", "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
    val draft = input("draft", "Create release as draft", default = false)

    override fun WorkflowBuilder.implementation() {
        job(id = "release", name = "GitHub Release", runsOn = UbuntuLatest) {
            uses(name = "Check out", action = Checkout(fetchDepth = Checkout.FetchDepth.Value(0)))
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction_Untyped(
                    configuration_Untyped = changelogConfig.expr,
                    toTag_Untyped = $$"${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf("GITHUB_TOKEN" to $$"${{ secrets.GITHUB_TOKEN }}"),
            )
            uses(
                name = "Create Release",
                action = ActionGhRelease(
                    body = $$"${{ steps.changelog.outputs.changelog }}",
                    name = $$"${{ github.ref_name }}",
                    tagName = $$"${{ github.ref_name }}",
                    draft_Untyped = draft.expr,
                ),
                env = linkedMapOf("GITHUB_TOKEN" to $$"${{ secrets.GITHUB_TOKEN }}"),
            )
        }
    }
}

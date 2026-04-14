package workflows

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.refInput
import workflows.core.ProjectWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object ReleaseWorkflow : ProjectWorkflow("release.yml") {

    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG
    )
    val draft = booleanInput(
        "draft",
        description = "Create release as draft",
        default = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        var changelogConfig by refInput(ReleaseWorkflow.changelogConfig)
        var draft by refInput(ReleaseWorkflow.draft)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    fun generate() {
        workflow(
            name = "Release",
            on = listOf(
                toWorkflowCallTrigger(),
            ),
            sourceFile = File("src/main/kotlin/workflows/ReleaseWorkflow.kt"),
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
                        configuration_Untyped = changelogConfig.ref.expression,
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
                        draft_Untyped = draft.ref.expression,
                    ),
                    env = linkedMapOf(
                        "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                    ),
                )
            }
        }
    }
}

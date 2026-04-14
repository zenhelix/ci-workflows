package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ReleaseWorkflow

object AppReleaseAdapter : AdapterWorkflow("app-release.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Application Release"

    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )
    val draft = booleanInput(
        "draft",
        description = "Create release as draft (default true for apps)",
        default = true,
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@AppReleaseAdapter.changelogConfig.ref.expression
            draft = this@AppReleaseAdapter.draft.ref.expression
        },
    )
}

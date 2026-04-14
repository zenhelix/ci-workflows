package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.ReleaseWorkflow

object AppReleaseAdapter : ProjectAdapterWorkflow("app-release.yml") {
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
            changelogConfig = this@AppReleaseAdapter.changelogConfig.ref
            draft = this@AppReleaseAdapter.draft.ref
        },
    )
}

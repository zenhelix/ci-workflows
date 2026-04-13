package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.AdapterWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object AppReleaseAdapter : AdapterWorkflow("app-release.yml") {
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

    override fun createJobBuilder() = ReleaseWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ReleaseWorkflow.JobBuilder>(id = "release", uses = ReleaseWorkflow) {
            changelogConfig(this@AppReleaseAdapter.changelogConfig.ref)
            draft(this@AppReleaseAdapter.draft.ref)
        },
    )
}

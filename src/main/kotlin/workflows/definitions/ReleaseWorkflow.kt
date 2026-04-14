package workflows.definitions

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.ReusableWorkflowJobBuilder
import dsl.refInput
import workflows.ProjectWorkflow

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
}

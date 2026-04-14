package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.AdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.ReleaseWorkflow

val appRelease: AdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
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

    ReleaseWorkflow.job("release") {
        ReleaseWorkflow.changelogConfig from changelogConfig
        ReleaseWorkflow.draft from draft
    }
}

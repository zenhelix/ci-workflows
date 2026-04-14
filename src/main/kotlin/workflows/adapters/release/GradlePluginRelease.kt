package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.PublishWorkflow
import workflows.ReleaseWorkflow
import workflows.setup

object GradlePluginReleaseAdapter : ProjectAdapterWorkflow("gradle-plugin-release.yml") {
    override val workflowName = "Gradle Plugin Release"

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@GradlePluginReleaseAdapter.changelogConfig.ref
        },
        reusableJob(id = "publish", uses = PublishWorkflow, PublishWorkflow::JobBuilder) {
            needs("release")
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            publishCommand = this@GradlePluginReleaseAdapter.publishCommand.ref
            passthroughAllSecrets()
        },
    )
}

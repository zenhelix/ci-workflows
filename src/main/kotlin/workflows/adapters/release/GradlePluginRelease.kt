package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradlePluginReleaseAdapter : AdapterWorkflow("gradle-plugin-release.yml") {
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

    override fun createJobBuilder() = PublishWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ReleaseWorkflow.JobBuilder>(id = "release", uses = ReleaseWorkflow) {
            changelogConfig(this@GradlePluginReleaseAdapter.changelogConfig.ref)
        },
        reusableJob<PublishWorkflow.JobBuilder>(id = "publish", uses = PublishWorkflow) {
            needs("release")
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            publishCommand(this@GradlePluginReleaseAdapter.publishCommand.ref)
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
                PublishWorkflow.gradlePublishKey,
                PublishWorkflow.gradlePublishSecret,
            )
        },
    )
}

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

object KotlinLibraryReleaseAdapter : ProjectAdapterWorkflow("kotlin-library-release.yml") {
    override val workflowName = "Kotlin Library Release"

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = "Gradle publish command for Maven Central",
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@KotlinLibraryReleaseAdapter.changelogConfig.ref
        },
        reusableJob(id = "publish", uses = PublishWorkflow, PublishWorkflow::JobBuilder) {
            needs("release")
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            publishCommand = this@KotlinLibraryReleaseAdapter.publishCommand.ref
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
            )
        },
    )
}

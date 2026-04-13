package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object KotlinLibraryReleaseAdapter : AdapterWorkflow("kotlin-library-release.yml") {
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

    init {
        secrets(MAVEN_SONATYPE_SECRETS)
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig(changelogConfig.ref)
        },
        reusableJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction(SetupTool.Gradle.id)
            PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            PublishWorkflow.publishCommand(publishCommand.ref)
            secrets(MAVEN_SONATYPE_SECRETS.passthrough())
        },
    )
}

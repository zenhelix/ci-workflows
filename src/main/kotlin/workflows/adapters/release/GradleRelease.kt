package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.PublishWorkflow
import workflows.definitions.ReleaseWorkflow
import workflows.setup

fun gradleReleaseWorkflow(
    fileName: String,
    name: String,
    publishDescription: String,
    publishSecrets: PublishWorkflow.JobBuilder.() -> Unit = { passthroughAllSecrets() },
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = publishDescription,
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    ReleaseWorkflow.job("release") {
        ReleaseWorkflow.changelogConfig from changelogConfig
    }

    PublishWorkflow.job("publish") {
        needs("release")
        setup(SetupTool.Gradle, javaVersion.ref.expression)
        PublishWorkflow.publishCommand from publishCommand
        publishSecrets()
    }
}

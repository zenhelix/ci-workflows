package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.SetupAwareJobBuilder
import dsl.builder.adapterWorkflow
import dsl.core.expr
import workflows.base.PublishWorkflow
import workflows.base.ReleaseWorkflow
import workflows.support.setup

object ReleaseAdapters {
    val app: AdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
        val draft = input("draft", description = "Create release as draft (default true for apps)", default = true)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
            ReleaseWorkflow.draft from draft
        }
    }

    val gradlePlugin = gradleRelease(
        "gradle-plugin-release.yml", "Gradle Plugin Release",
        "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    )

    val kotlinLibrary = gradleRelease(
        "kotlin-library-release.yml", "Kotlin Library Release",
        "Gradle publish command for Maven Central",
    ) {
        passthroughSecrets(PublishWorkflow.mavenSecrets)
    }

    private fun gradleRelease(
        fileName: String,
        name: String,
        publishDescription: String,
        publishSecrets: SetupAwareJobBuilder<PublishWorkflow>.() -> Unit = { passthroughAllSecrets() },
    ): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input(SetupTool.Gradle.versionKey, description = SetupTool.Gradle.versionDescription, default = SetupTool.Gradle.defaultVersion)
        val publishCommand = input("publish-command", description = publishDescription, required = true)
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
        }

        PublishWorkflow.job("publish") {
            needs("release")
            setup(SetupTool.Gradle, javaVersion.expr)
            PublishWorkflow.publishCommand from publishCommand
            publishSecrets()
        }
    }
}

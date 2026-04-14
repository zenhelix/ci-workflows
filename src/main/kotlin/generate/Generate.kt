package generate

import config.SetupTool
import workflows.CheckWorkflow
import workflows.ConventionalCommitCheckWorkflow
import workflows.CreateTagWorkflow
import workflows.ManualCreateTagWorkflow
import workflows.ReleaseWorkflow
import workflows.PublishWorkflow
import workflows.LabelerWorkflow
import workflows.AppDeployWorkflow
import workflows.adapters.check.gradleCheck
import workflows.adapters.release.appRelease
import workflows.adapters.release.gradleReleaseWorkflow
import workflows.adapters.tag.toolCreateTag
import workflows.adapters.tag.toolManualCreateTag
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    generateBaseWorkflows()
    generateAdapterWorkflows(outputDir)
}

private fun generateBaseWorkflows() {
    CheckWorkflow.generate()
    ConventionalCommitCheckWorkflow.generate()
    CreateTagWorkflow.generate()
    ManualCreateTagWorkflow.generate()
    ReleaseWorkflow.generate()
    PublishWorkflow.generate()
    LabelerWorkflow.generate()
    AppDeployWorkflow.generate()
}

private fun generateAdapterWorkflows(outputDir: File) {
    // Check adapters
    gradleCheck("app-check.yml", "Application Check").generate(outputDir)
    gradleCheck("gradle-check.yml", "Gradle Check").generate(outputDir)
    gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    gradleCheck("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)

    // Tag adapters
    toolCreateTag(
        fileName = "gradle-create-tag.yml",
        name = "Gradle Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolCreateTag(
        fileName = "go-create-tag.yml",
        name = "Go Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "gradle-manual-create-tag.yml",
        name = "Gradle Manual Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "go-manual-create-tag.yml",
        name = "Go Manual Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)

    // Release adapters
    appRelease.generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "gradle-plugin-release.yml",
        name = "Gradle Plugin Release",
        publishDescription = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    ).generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "kotlin-library-release.yml",
        name = "Kotlin Library Release",
        publishDescription = "Gradle publish command for Maven Central",
        publishSecrets = {
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
            )
        },
    ).generate(outputDir)
}

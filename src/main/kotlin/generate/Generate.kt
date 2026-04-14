package generate

import config.DEFAULT_GO_VERSION
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import workflows.adapters.check.GradleCheckAdapter
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
import workflows.adapters.tag.CreateTagAdapter
import workflows.adapters.tag.ManualCreateTagAdapter
import workflows.base.generateAppDeploy
import workflows.base.generateCheck
import workflows.base.generateConventionalCommitCheck
import workflows.base.generateCreateTag
import workflows.base.generateLabeler
import workflows.base.generateManualCreateTag
import workflows.base.generatePublish
import workflows.base.generateRelease
import java.io.File

fun main() {
    val outputDir = File(".github/workflows")

    generateCheck()
    generateConventionalCommitCheck()
    generateCreateTag()
    generateManualCreateTag()
    generateRelease()
    generatePublish()
    generateLabeler()
    generateAppDeploy()

    // Adapters
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
    AppReleaseAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
    CreateTagAdapter(
        fileName = "gradle-create-tag.yml",
        workflowName = "Gradle Create Tag",
        tool = SetupTool.Gradle,
        defaultVersion = DEFAULT_JAVA_VERSION,
        versionDescription = "JDK version to use",
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    CreateTagAdapter(
        fileName = "go-create-tag.yml",
        workflowName = "Go Create Tag",
        tool = SetupTool.Go,
        defaultVersion = DEFAULT_GO_VERSION,
        versionDescription = "Go version to use",
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
    ManualCreateTagAdapter(
        fileName = "gradle-manual-create-tag.yml",
        workflowName = "Gradle Manual Create Tag",
        tool = SetupTool.Gradle,
        defaultVersion = DEFAULT_JAVA_VERSION,
        versionDescription = "JDK version to use",
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    ManualCreateTagAdapter(
        fileName = "go-manual-create-tag.yml",
        workflowName = "Go Manual Create Tag",
        tool = SetupTool.Go,
        defaultVersion = DEFAULT_GO_VERSION,
        versionDescription = "Go version to use",
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
}

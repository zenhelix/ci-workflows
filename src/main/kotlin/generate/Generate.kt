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
    CreateTagAdapter("gradle-create-tag.yml", "Gradle Create Tag", SetupTool.Gradle, DEFAULT_JAVA_VERSION, "gradle-command", "./gradlew check", "").generate(outputDir)
    CreateTagAdapter("go-create-tag.yml", "Go Create Tag", SetupTool.Go, DEFAULT_GO_VERSION, "check-command", "make test", "v").generate(outputDir)
    ManualCreateTagAdapter("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", SetupTool.Gradle, DEFAULT_JAVA_VERSION, "gradle-command", "./gradlew check", "").generate(outputDir)
    ManualCreateTagAdapter("go-manual-create-tag.yml", "Go Manual Create Tag", SetupTool.Go, DEFAULT_GO_VERSION, "check-command", "make test", "v").generate(outputDir)
}

package generate

import workflows.adapters.check.GradleCheckAdapter
import workflows.base.generateAppDeploy
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
import workflows.adapters.tag.GradleCreateTagAdapter
import workflows.adapters.tag.GradleManualCreateTagAdapter
import workflows.adapters.tag.GoCreateTagAdapter
import workflows.adapters.tag.GoManualCreateTagAdapter
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

    // Adapters
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
    AppReleaseAdapter.generate(outputDir)
    generateAppDeploy(outputDir)
    GradleCreateTagAdapter.generate(outputDir)
    GradleManualCreateTagAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
    GoCreateTagAdapter.generate(outputDir)
    GoManualCreateTagAdapter.generate(outputDir)
}

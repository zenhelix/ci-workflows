import workflows.adapters.generateAppCheck
import workflows.adapters.generateAppDeploy
import workflows.adapters.generateAppRelease
import workflows.adapters.generateGoCreateTag
import workflows.adapters.generateGoManualCreateTag
import workflows.adapters.generateGradleCreateTag
import workflows.adapters.generateGradleManualCreateTag
import workflows.adapters.generateGradlePluginCheck
import workflows.adapters.generateGradlePluginRelease
import workflows.adapters.generateKotlinLibraryCheck
import workflows.adapters.generateKotlinLibraryRelease
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

    generateCheck(outputDir)
    generateConventionalCommitCheck(outputDir)
    generateCreateTag(outputDir)
    generateManualCreateTag(outputDir)
    generateRelease(outputDir)
    generatePublish(outputDir)
    generateLabeler(outputDir)

    // Adapters
    generateAppCheck(outputDir)
    generateAppRelease(outputDir)
    generateAppDeploy(outputDir)
    generateGradleCreateTag(outputDir)
    generateGradleManualCreateTag(outputDir)
    generateGradlePluginCheck(outputDir)
    generateGradlePluginRelease(outputDir)
    generateKotlinLibraryCheck(outputDir)
    generateKotlinLibraryRelease(outputDir)
    generateGoCreateTag(outputDir)
    generateGoManualCreateTag(outputDir)
}

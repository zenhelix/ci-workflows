import workflows.adapters.*
import workflows.base.*
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

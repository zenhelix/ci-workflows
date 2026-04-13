package generate

import workflows.adapters.check.generateAppCheck
import workflows.adapters.check.generateGradlePluginCheck
import workflows.adapters.check.generateKotlinLibraryCheck
import workflows.adapters.deploy.generateAppDeploy
import workflows.adapters.release.generateAppRelease
import workflows.adapters.release.generateGradlePluginRelease
import workflows.adapters.release.generateKotlinLibraryRelease
import workflows.adapters.tag.generateGoCreateTag
import workflows.adapters.tag.generateGoManualCreateTag
import workflows.adapters.tag.generateGradleCreateTag
import workflows.adapters.tag.generateGradleManualCreateTag
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

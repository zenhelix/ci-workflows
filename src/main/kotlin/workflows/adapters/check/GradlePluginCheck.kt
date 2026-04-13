package workflows.adapters.check

import java.io.File

fun generateGradlePluginCheck(outputDir: File) {
    generateGradleCheckWorkflow("Gradle Plugin Check", "gradle-plugin-check", outputDir)
}

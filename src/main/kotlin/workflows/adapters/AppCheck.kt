package workflows.adapters

import java.io.File

fun generateAppCheck(outputDir: File) {
    generateGradleCheckWorkflow("Application Check", "app-check", outputDir)
}

package workflows.adapters.check

import java.io.File

fun generateAppCheck(outputDir: File) {
    generateGradleCheckWorkflow("Application Check", "app-check", outputDir)
}

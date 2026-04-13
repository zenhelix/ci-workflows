package workflows.adapters

import java.io.File

fun generateKotlinLibraryCheck(outputDir: File) {
    generateGradleCheckWorkflow("Kotlin Library Check", "kotlin-library-check", outputDir)
}

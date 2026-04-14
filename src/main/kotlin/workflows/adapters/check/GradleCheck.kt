package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.CheckWorkflow
import workflows.base.ConventionalCommitCheckWorkflow
import workflows.support.setup

object GradleCheck {
    val appCheck = gradleCheck("app-check.yml", "Application Check")
    val gradleCheck = gradleCheck("gradle-check.yml", "Gradle Check")
    val gradlePluginCheck = gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check")
    val kotlinLibraryCheck = gradleCheck("kotlin-library-check.yml", "Kotlin Library Check")

    private fun gradleCheck(fileName: String, name: String): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
        val javaVersions = input("java-versions", description = "JSON array of JDK versions for matrix build (overrides java-version)", default = "")
        val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")

        val javaVersionMatrix = matrixRef("java-version")

        ConventionalCommitCheckWorkflow.job("conventional-commit")

        CheckWorkflow.job("check") {
            strategy(matrix(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR))
            setup(SetupTool.Gradle, javaVersionMatrix.ref)
            CheckWorkflow.checkCommand from gradleCommand
        }
    }
}

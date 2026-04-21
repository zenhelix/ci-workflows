package workflows.adapters.check

import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import dsl.capability.setupJob
import dsl.core.expr
import dsl.core.simpleJob
import workflows.base.CheckWorkflow
import workflows.base.ConventionalCommitCheckWorkflow
import workflows.base.ShaPinningGuardWorkflow
import workflows.support.setup

object GradleCheck {
    val appCheck = gradleCheck("app-check.yml", "Application Check")
    val gradleCheck = gradleCheck("gradle-check.yml", "Gradle Check")
    val gradlePluginCheck = gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check")
    val kotlinLibraryCheck = gradleCheck("kotlin-library-check.yml", "Kotlin Library Check")

    val all: List<AdapterWorkflow> = listOf(appCheck, gradleCheck, gradlePluginCheck, kotlinLibraryCheck)

    private fun gradleCheck(fileName: String, name: String): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input(SetupTool.Gradle.versionKey, description = SetupTool.Gradle.versionDescription, default = SetupTool.Gradle.defaultVersion)
        val javaVersions = input("java-versions", description = "JSON array of JDK versions for matrix build (overrides java-version)", default = "")
        val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")

        val javaVersionMatrix = matrixRef("java-version")

        ConventionalCommitCheckWorkflow.simpleJob("conventional-commit")

        ShaPinningGuardWorkflow.simpleJob("sha-pin-check")

        CheckWorkflow.setupJob("check") {
            strategy(matrix(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR))
            setup(SetupTool.Gradle, javaVersionMatrix.expr)
            CheckWorkflow.checkCommand from gradleCommand
        }
    }
}

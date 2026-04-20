package workflows.base

import dsl.core.expr
import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.actions.actions.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.github.CodeqlActionAnalyze_Untyped
import io.github.typesafegithub.workflows.actions.github.CodeqlActionInit_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle_Untyped
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object CodeqlAnalysisWorkflow : ProjectWorkflow(
    "codeql-analysis.yml",
    "CodeQL Analysis",
    permissions = mapOf(
        Permission.SecurityEvents to Mode.Write,
        Permission.Contents to Mode.Read,
        Permission.Actions to Mode.Read,
    ),
) {
    val language = input("language", "CodeQL language to analyze", default = "java-kotlin")
    val javaVersion = input("java-version", "JDK version to use for build", default = "17")
    val buildCommand = input(
        "build-command",
        "Build command to compile sources for CodeQL",
        default = "./gradlew classes testClasses",
    )

    override fun WorkflowBuilder.implementation() {
        job(
            id = "analyze",
            name = "Analyze",
            runsOn = UbuntuLatest,
            timeoutMinutes = 30,
            _customArguments = linkedMapOf(
                "strategy" to linkedMapOf(
                    "fail-fast" to false,
                    "matrix" to linkedMapOf(
                        "language" to listOf(language.expr),
                    ),
                ),
            ),
        ) {
            uses(
                name = "Checkout",
                action = Checkout_Untyped(),
            )
            uses(
                name = "Initialize CodeQL",
                action = CodeqlActionInit_Untyped(
                    languages_Untyped = $$"${{ matrix.language }}",
                ),
            )
            uses(
                name = "Setup Java",
                action = SetupJava_Untyped(
                    distribution_Untyped = "temurin",
                    javaVersion_Untyped = javaVersion.expr,
                ),
            )
            uses(
                name = "Setup Gradle",
                action = ActionsSetupGradle_Untyped(),
            )
            run(
                name = "Build",
                command = buildCommand.expr,
            )
            uses(
                name = "Perform CodeQL Analysis",
                action = CodeqlActionAnalyze_Untyped(
                    category_Untyped = $$"/language:${{ matrix.language }}",
                ),
            )
        }
    }
}

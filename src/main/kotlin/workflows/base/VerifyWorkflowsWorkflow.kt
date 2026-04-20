package workflows.base

import dsl.builder.GeneratableWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.actions.actions.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle_Untyped
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object VerifyWorkflowsWorkflow : GeneratableWorkflow {
    override val fileName: String = "verify-workflows.yml"

    private val PATHS = listOf(
        "src/**",
        "workflow-dsl/**",
        "build.gradle.kts",
        ".github/workflows/**",
    )

    private val CHECK_SCRIPT = """
        if ! git diff --exit-code .github/workflows/; then
          echo "::error::Generated workflow YAML files are out of date. Run ./gradlew run and commit."
          exit 1
        fi
        echo "All generated workflows are up-to-date."
    """.trimIndent()

    override fun generate(outputDir: File) {
        workflow(
            name = "Verify Workflows",
            on = listOf(
                PullRequest(paths = PATHS),
                Push(branches = listOf("main"), paths = PATHS),
            ),
            sourceFile = File("src/main/kotlin/workflows/base/VerifyWorkflowsWorkflow.kt"),
            targetFileName = fileName,
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(Permission.Contents to Mode.Read),
        ) {
            job(
                id = "verify",
                name = "Verify generated workflows are up-to-date",
                runsOn = UbuntuLatest,
            ) {
                uses(name = "Check out", action = Checkout_Untyped())
                uses(
                    name = "Set up JDK",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "temurin",
                        javaVersion_Untyped = "17",
                    ),
                )
                uses(name = "Setup Gradle", action = ActionsSetupGradle_Untyped())
                run(name = "Generate workflows", command = "./gradlew run")
                run(name = "Check for differences", command = CHECK_SCRIPT)
            }
        }
    }
}

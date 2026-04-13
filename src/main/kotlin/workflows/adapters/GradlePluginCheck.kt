package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateGradlePluginCheck(outputDir: File) {
    val targetFile = "gradle-plugin-check.yml"

    val JAVA_VERSION_MATRIX_EXPR =
        "\${{ fromJson(inputs.java-versions || format('[\"" + "{0}" + "\"]', inputs.java-version)) }}"

    workflow(
        name = "Gradle Plugin Check",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/gradle-plugin-check.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "java-version" to stringInput(
                            description = "JDK version to use",
                            default = DEFAULT_JAVA_VERSION,
                        ),
                        "java-versions" to stringInput(
                            description = "JSON array of JDK versions for matrix build (overrides java-version)",
                            default = "",
                        ),
                        "gradle-command" to stringInput(
                            description = "Gradle check command",
                            default = "./gradlew check",
                        ),
                    ),
                ),
            ),
        ),
    ) {
        job(
            id = "conventional-commit",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "uses" to reusableWorkflow("conventional-commit-check.yml"),
            ),
        ) {
            noop()
        }

        job(
            id = "check",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "strategy" to mapOf(
                    "matrix" to mapOf(
                        "java-version" to JAVA_VERSION_MATRIX_EXPR,
                    ),
                ),
                "uses" to reusableWorkflow("check.yml"),
                "with" to mapOf(
                    "setup-action" to "gradle",
                    "setup-params" to "{\"java-version\": \"\${{ matrix.java-version }}\"}",
                    "check-command" to "\${{ inputs.gradle-command }}",
                ),
            ),
        ) {
            noop()
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_JAVA_VERSION
import shared.JAVA_VERSION_MATRIX_EXPR
import shared.cleanReusableWorkflowJobs
import shared.dsl.CheckWorkflow
import shared.dsl.ConventionalCommitCheckWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradlePluginCheck(outputDir: File) {
    val targetFile = "gradle-plugin-check.yml"

    workflow(
        name = "Gradle Plugin Check",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
                "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
            )),
        ),
        sourceFile = File(".github/workflow-src/gradle-plugin-check.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(
            id = "conventional-commit",
            uses = ConventionalCommitCheckWorkflow,
        )

        reusableWorkflowJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction("gradle")
            CheckWorkflow.setupParams("{\"java-version\": \"\${{ matrix.java-version }}\"}")
            CheckWorkflow.checkCommand("\${{ inputs.gradle-command }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

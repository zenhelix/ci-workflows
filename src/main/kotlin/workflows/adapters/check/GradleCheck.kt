package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

internal fun generateGradleCheckWorkflow(
    workflowName: String,
    fileSlug: String,
    outputDir: File,
) {
    val targetFile = "$fileSlug.yml"

    workflow(
        name = workflowName,
        on = listOf(
            WorkflowCall(inputs = mapOf(
                "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
                "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
            )),
        ),
        sourceFile = File(".github/workflow-src/$fileSlug.main.kts"),
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

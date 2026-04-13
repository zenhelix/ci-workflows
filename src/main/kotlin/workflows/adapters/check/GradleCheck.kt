package workflows.adapters.check

import config.CommonInputs
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.inputRef
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
                CommonInputs.javaVersion(),
                CommonInputs.javaVersions(),
                CommonInputs.gradleCommand(),
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
            CheckWorkflow.setupAction(SetupTool.Gradle.id)
            CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
            CheckWorkflow.checkCommand(inputRef("gradle-command"))
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

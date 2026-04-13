package workflows.adapters.check

import config.CommonInputs
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

internal fun generateGradleCheckWorkflow(
    workflowName: String,
    fileSlug: String,
    outputDir: File,
) {
    val targetFile = "$fileSlug.yml"

    generateAdapterWorkflow(
        name = workflowName,
        sourceFileSlug = fileSlug,
        targetFileName = targetFile,
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.javaVersions(),
                CommonInputs.gradleCommand(),
            )
        ),
        jobs = listOf(
            reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
            reusableJob(id = "check", uses = CheckWorkflow) {
                strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
                CheckWorkflow.setupAction(SetupTool.Gradle.id)
                CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
                CheckWorkflow.checkCommand(inputRef("gradle-command"))
            },
        ),
        outputDir = outputDir,
    )
}

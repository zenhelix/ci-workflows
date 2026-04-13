package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

class GradleCheckAdapter(
    fileName: String,
    override val workflowName: String,
) : AdapterWorkflow(fileName) {

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val javaVersions = input(
        "java-versions",
        description = "JSON array of JDK versions for matrix build (overrides java-version)",
        default = "",
    )
    val gradleCommand = input(
        "gradle-command",
        description = "Gradle check command",
        default = "./gradlew check",
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
        reusableJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction(SetupTool.Gradle.id)
            CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
            CheckWorkflow.checkCommand(gradleCommand.ref)
        },
    )
}

package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.MatrixDef
import dsl.MatrixRef
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

    private val javaVersionMatrix = MatrixRef("java-version")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ConventionalCommitCheckWorkflow.JobBuilder>(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
        reusableJob<CheckWorkflow.JobBuilder>(id = "check", uses = CheckWorkflow) {
            strategy(MatrixDef(mapOf(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR)))
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersionMatrix.ref))
            checkCommand(gradleCommand.ref)
        },
    )
}

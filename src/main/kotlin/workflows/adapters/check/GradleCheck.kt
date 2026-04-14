package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.MatrixDef
import dsl.MatrixRef
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.CheckWorkflow
import workflows.ConventionalCommitCheckWorkflow
import workflows.setup

class GradleCheckAdapter(
    fileName: String,
    override val workflowName: String,
) : AdapterWorkflow(fileName) {
    override val usesString = reusableWorkflow(fileName)

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
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow, ConventionalCommitCheckWorkflow::JobBuilder),
        reusableJob(id = "check", uses = CheckWorkflow, CheckWorkflow::JobBuilder) {
            strategy(MatrixDef(mapOf(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR)))
            setup(config.SetupTool.Gradle, javaVersionMatrix.ref)
            checkCommand = gradleCommand.ref.expression
        },
    )
}

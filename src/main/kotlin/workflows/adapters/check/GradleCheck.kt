package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import dsl.MatrixDef
import dsl.MatrixRef
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.definitions.CheckWorkflow
import workflows.definitions.ConventionalCommitCheckWorkflow
import workflows.ProjectAdapterWorkflow
import workflows.setup

class GradleCheckAdapter(
    fileName: String,
    override val workflowName: String,
) : ProjectAdapterWorkflow(fileName) {

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
            checkCommand = gradleCommand.ref
        },
    )
}

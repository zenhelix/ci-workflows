package workflows

import config.reusableWorkflow
import dsl.builder.GeneratableWorkflow
import dsl.core.ReusableWorkflow
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

abstract class ProjectWorkflow(
    fileName: String,
    private val workflowName: String,
    private val permissions: Map<Permission, Mode>? = mapOf(Permission.Contents to Mode.Read),
) : ReusableWorkflow(fileName), GeneratableWorkflow {

    override val usesString: String = reusableWorkflow(fileName)

    protected abstract fun WorkflowBuilder.implementation()

    override fun generate(outputDir: File) {
        workflow(
            name = workflowName,
            on = listOf(toWorkflowCallTrigger()),
            sourceFile = sourceFile(),
            targetFileName = fileName,
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = permissions,
        ) {
            implementation()
        }
    }

    private fun sourceFile(): File {
        val className = this::class.simpleName ?: error("Anonymous workflow")
        return File("src/main/kotlin/workflows/base/$className.kt")
    }
}

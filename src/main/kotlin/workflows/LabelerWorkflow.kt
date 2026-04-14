package workflows

import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.refInput
import workflows.core.ProjectWorkflow
import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object LabelerWorkflow : ProjectWorkflow("labeler.yml") {

    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by refInput(LabelerWorkflow.configPath)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    fun generate() {
        workflow(
            name = "PR Labeler",
            on = listOf(
                WorkflowCall(inputs = inputs),
            ),
            sourceFile = File("src/main/kotlin/workflows/LabelerWorkflow.kt"),
            targetFileName = "labeler.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(
                Permission.Contents to Mode.Write,
                Permission.PullRequests to Mode.Write,
            ),
        ) {
            job(
                id = "label",
                name = "Label PR",
                runsOn = UbuntuLatest,
            ) {
                uses(
                    name = "Label PR based on file paths",
                    action = Labeler(
                        repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
                        configurationPath_Untyped = configPath.ref.expression,
                        syncLabels = true,
                    ),
                )
            }
        }
    }
}

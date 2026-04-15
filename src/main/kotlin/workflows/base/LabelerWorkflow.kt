package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.core.expr
import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object LabelerWorkflow : ProjectWorkflow(
    "labeler.yml", "PR Labeler",
    permissions = mapOf(Permission.Contents to Mode.Write, Permission.PullRequests to Mode.Write),
) {
    val configPath = input("config-path", "Path to labeler configuration file", default = ".github/labeler.yml")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: ReusableWorkflowJobBuilder.() -> Unit = {}) =
        job(id, { ReusableWorkflowJobBuilder(this@LabelerWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "label", name = "Label PR", runsOn = UbuntuLatest) {
            uses(
                name = "Label PR based on file paths",
                action = Labeler(
                    repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath_Untyped = configPath.expr,
                    syncLabels = true,
                ),
            )
        }
    }
}

package workflows

import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.refInput
import workflows.core.ProjectWorkflow
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object ConventionalCommitCheckWorkflow : ProjectWorkflow("conventional-commit-check.yml") {

    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by refInput(ConventionalCommitCheckWorkflow.allowedTypes)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    fun generate() {
        workflow(
            name = "Conventional Commit Check",
            on = listOf(
                WorkflowCall(inputs = inputs),
            ),
            sourceFile = File("src/main/kotlin/workflows/ConventionalCommitCheckWorkflow.kt"),
            targetFileName = "conventional-commit-check.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        ) {
            job(
                id = "check-title",
                name = "Check PR Title",
                runsOn = UbuntuLatest,
            ) {
                run(
                    name = "Validate PR title format",
                    command = """
                        TYPES_PATTERN=${'$'}(echo "${'$'}ALLOWED_TYPES" | tr ',' '|')
                        PATTERN="^(${'$'}TYPES_PATTERN)(\(.+\))?(!)?: .+"
                        if [[ ! "${'$'}PR_TITLE" =~ ${'$'}PATTERN ]]; then
                          echo "::warning::PR title does not match conventional commits format: <type>(<scope>): <description>"
                          echo "::warning::Allowed types: ${'$'}ALLOWED_TYPES"
                          echo "::warning::Got: ${'$'}PR_TITLE"
                        else
                          echo "PR title is valid: ${'$'}PR_TITLE"
                        fi
                    """.trimIndent(),
                    env = linkedMapOf(
                        "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                        "ALLOWED_TYPES" to allowedTypes.ref.expression,
                    ),
                )
            }
        }
    }
}

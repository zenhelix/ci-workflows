package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import dsl.ConventionalCommitCheckWorkflow
import java.io.File

fun generateConventionalCommitCheck() {
    workflow(
        name = "Conventional Commit Check",
        on = listOf(
            WorkflowCall(inputs = ConventionalCommitCheckWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/conventional-commit-check.main.kts"),
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
                    "ALLOWED_TYPES" to ConventionalCommitCheckWorkflow.allowedTypes.ref,
                ),
            )
        }
    }
}

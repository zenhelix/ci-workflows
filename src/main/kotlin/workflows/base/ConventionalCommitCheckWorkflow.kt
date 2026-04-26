package workflows.base

import dsl.core.expr
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

private val VALIDATE_PR_TITLE_SCRIPT = $$"""
    TYPES_PATTERN=$(echo "$ALLOWED_TYPES" | tr ',' '|')
    PATTERN="^($TYPES_PATTERN)(\(.+\))?(!)?: .+"
    if [[ ! "$PR_TITLE" =~ $PATTERN ]]; then
      echo "::warning::PR title does not match conventional commits format: <type>(<scope>): <description>"
      echo "::warning::Allowed types: $ALLOWED_TYPES"
      echo "::warning::Got: $PR_TITLE"
    else
      echo "PR title is valid: $PR_TITLE"
    fi
""".trimIndent()

object ConventionalCommitCheckWorkflow : ProjectWorkflow(
    "conventional-commit-check.yml", "Conventional Commit Check",
    permissions = null,
    concurrency = Concurrency(
        group = "\${{ github.workflow }}-\${{ github.ref }}",
        cancelInProgress = true,
    ),
) {
    val allowedTypes = input("allowed-types", "Comma-separated list of allowed commit types", default = "feat,fix,refactor,docs,test,chore,perf,ci")

    override fun WorkflowBuilder.implementation() {
        job(
            id = "check-title",
            name = "Check PR Title",
            runsOn = UbuntuLatest,
            timeoutMinutes = 5,
            condition = $$"${{ github.event_name == 'pull_request' }}",
        ) {
            run(
                name = "Validate PR title format",
                command = VALIDATE_PR_TITLE_SCRIPT,
                env = linkedMapOf(
                    "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                    "ALLOWED_TYPES" to allowedTypes.expr,
                ),
            )
        }
    }
}

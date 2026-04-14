package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.refInput
import workflows.ProjectWorkflow

object ConventionalCommitCheckWorkflow : ProjectWorkflow("conventional-commit-check.yml") {

    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by refInput(ConventionalCommitCheckWorkflow.allowedTypes)
    }
}

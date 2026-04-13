#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Conventional Commit Check",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "conventional-commit-check.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "allowed-types" to stringInput(
                        description = "Comma-separated list of allowed commit types",
                        default = "feat,fix,refactor,docs,test,chore,perf,ci",
                    ),
                ),
            ),
        ),
        "permissions" to mapOf("pull-requests" to "read"),
    ),
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
                  echo "::error::PR title must match conventional commits format: <type>(<scope>): <description>"
                  echo "::error::Allowed types: ${'$'}ALLOWED_TYPES"
                  echo "::error::Got: ${'$'}PR_TITLE"
                  exit 1
                fi
                echo "PR title is valid: ${'$'}PR_TITLE"
            """.trimIndent(),
            env = linkedMapOf(
                "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                "ALLOWED_TYPES" to "\${{ inputs.allowed-types }}",
            ),
        )
    }
}

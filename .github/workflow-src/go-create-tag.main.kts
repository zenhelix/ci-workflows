#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Go Create Tag",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "go-create-tag.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "go-version" to stringInput(
                        description = "Go version to use",
                        default = DEFAULT_GO_VERSION,
                    ),
                    "check-command" to stringInput(
                        description = "Go validation command",
                        default = "make test",
                    ),
                    "default-bump" to stringInput(
                        description = "Default version bump type (major, minor, patch)",
                        default = "patch",
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag",
                        default = "v",
                    ),
                    "release-branches" to stringInput(
                        description = "Comma-separated branch patterns for releases",
                        default = DEFAULT_RELEASE_BRANCHES,
                    ),
                ),
                "secrets" to mapOf(
                    "app-id" to secretInput("", required = true),
                    "app-private-key" to secretInput("", required = true),
                ),
            ),
        ),
    ),
) {
    job(
        id = "create-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("create-tag.yml"),
            "with" to mapOf(
                "setup-action" to "go",
                "setup-params" to "{\"go-version\": \"\${{ inputs.go-version }}\"}",
                "check-command" to "\${{ inputs.check-command }}",
                "default-bump" to "\${{ inputs.default-bump }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "release-branches" to "\${{ inputs.release-branches }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {}
}

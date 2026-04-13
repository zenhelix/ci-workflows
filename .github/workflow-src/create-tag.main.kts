#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Create Tag",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "create-tag.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    "check-command" to stringInput(
                        description = "Validation command to run before tagging",
                        required = true,
                    ),
                    "default-bump" to stringInput(
                        description = "Default version bump type (major, minor, patch)",
                        default = "patch",
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag (e.g. v)",
                        default = "",
                    ),
                    "release-branches" to stringInput(
                        description = "Comma-separated branch patterns for releases",
                        default = DEFAULT_RELEASE_BRANCHES,
                    ),
                ),
                "secrets" to mapOf(
                    APP_ID_SECRET,
                    APP_PRIVATE_KEY_SECRET,
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    ),
) {
    job(
        id = "create_tag",
        name = "Create Tag",
        runsOn = UbuntuLatest,
    ) {
        conditionalSetupSteps(fetchDepth = "0")
        run(
            name = "Run validation",
            command = "\${{ inputs.check-command }}",
        )
        uses(
            name = "Generate App Token",
            action = CreateAppTokenAction(
                appId = "\${{ secrets.app-id }}",
                appPrivateKey = "\${{ secrets.app-private-key }}",
            ),
            id = "app-token",
        )
        uses(
            name = "Bump version and push tag",
            action = GithubTagAction(
                githubToken = "\${{ steps.app-token.outputs.token }}",
                defaultBump = "\${{ inputs.default-bump }}",
                tagPrefix = "\${{ inputs.tag-prefix }}",
                releaseBranches = "\${{ inputs.release-branches }}",
            ),
        )
    }
}

#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Check",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "check.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    CHECK_COMMAND_INPUT,
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "read"),
    ),
) {
    job(
        id = "build",
        name = "Build",
        runsOn = UbuntuLatest,
    ) {
        conditionalSetupSteps()
        run(
            name = "Run check",
            command = "\${{ inputs.check-command }}",
        )
    }
}

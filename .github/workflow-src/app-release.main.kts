#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val targetFile = "app-release.yml"

workflow(
    name = "Application Release",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = targetFile,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "changelog-config" to stringInput(
                        description = "Path to changelog configuration file",
                        default = DEFAULT_CHANGELOG_CONFIG,
                    ),
                    "draft" to booleanInput(
                        description = "Create release as draft (default true for apps)",
                        default = true,
                    ),
                ),
            ),
        ),
    ),
) {
    job(
        id = "release",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("release.yml"),
            "with" to mapOf(
                "changelog-config" to "\${{ inputs.changelog-config }}",
                "draft" to "\${{ inputs.draft }}",
            ),
        ),
    ) {
        noop()
    }
}

cleanReusableWorkflowJobs(targetFile)

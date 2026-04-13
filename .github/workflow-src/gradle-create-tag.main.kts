#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val targetFile = "gradle-create-tag.yml"

workflow(
    name = "Gradle Create Tag",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = targetFile,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(
                        description = "JDK version to use",
                        default = DEFAULT_JAVA_VERSION,
                    ),
                    "gradle-command" to stringInput(
                        description = "Gradle validation command",
                        default = "./gradlew check",
                    ),
                    "default-bump" to stringInput(
                        description = "Default version bump type (major, minor, patch)",
                        default = "patch",
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag",
                        default = "",
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
                "setup-action" to "gradle",
                "setup-params" to "{\"java-version\": \"\${{ inputs.java-version }}\"}",
                "check-command" to "\${{ inputs.gradle-command }}",
                "default-bump" to "\${{ inputs.default-bump }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "release-branches" to "\${{ inputs.release-branches }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {
        run(name = "placeholder", command = "echo placeholder")
    }
}

cleanReusableWorkflowJobs(targetFile)

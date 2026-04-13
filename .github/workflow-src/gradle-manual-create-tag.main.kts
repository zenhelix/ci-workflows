#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val targetFile = "gradle-manual-create-tag.yml"

workflow(
    name = "Gradle Manual Create Tag",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = targetFile,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(
                        description = "Version to tag (e.g. 1.2.3)",
                        required = true,
                    ),
                    "java-version" to stringInput(
                        description = "JDK version to use",
                        default = DEFAULT_JAVA_VERSION,
                    ),
                    "gradle-command" to stringInput(
                        description = "Gradle validation command",
                        default = "./gradlew check",
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag",
                        default = "",
                    ),
                ),
                "secrets" to mapOf(
                    APP_ID_SECRET,
                    APP_PRIVATE_KEY_SECRET,
                ),
            ),
        ),
    ),
) {
    job(
        id = "manual-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("manual-create-tag.yml"),
            "with" to mapOf(
                "tag-version" to "\${{ inputs.tag-version }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "setup-action" to "gradle",
                "setup-params" to "{\"java-version\": \"\${{ inputs.java-version }}\"}",
                "check-command" to "\${{ inputs.gradle-command }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {
        noop()
    }
}

cleanReusableWorkflowJobs(targetFile)

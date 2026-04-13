#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.actions.Action
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

class SetupGradleAction(
    private val javaVersion: String,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-gradle")
    override fun toYamlArguments() = linkedMapOf("java-version" to javaVersion)
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupGoAction(
    private val goVersion: String,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-go")
    override fun toYamlArguments() = linkedMapOf("go-version" to goVersion)
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupPythonAction(
    private val pythonVersion: String,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-python")
    override fun toYamlArguments() = linkedMapOf("python-version" to pythonVersion)
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

workflow(
    name = "Check",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
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
        uses(
            name = "Setup Gradle",
            action = SetupGradleAction(
                javaVersion = "\${{ fromJson(inputs.setup-params).java-version || '17' }}",
            ),
            condition = "inputs.setup-action == 'gradle'",
        )
        uses(
            name = "Setup Go",
            action = SetupGoAction(
                goVersion = "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}",
            ),
            condition = "inputs.setup-action == 'go'",
        )
        uses(
            name = "Setup Python",
            action = SetupPythonAction(
                pythonVersion = "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}",
            ),
            condition = "inputs.setup-action == 'python'",
        )
        run(
            name = "Run check",
            command = "\${{ inputs.check-command }}",
        )
    }
}

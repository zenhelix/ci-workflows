package shared

import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    uses(
        name = "Setup Gradle",
        action = SetupAction(
            "setup-gradle", "java-version",
            "\${{ fromJson(inputs.setup-params).java-version || '17' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupAction(
            "setup-go", "go-version",
            "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupAction(
            "setup-python", "python-version",
            "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'python'",
    )
}

fun JobBuilder<*>.noop() {
    run(name = "noop", command = "true")
}

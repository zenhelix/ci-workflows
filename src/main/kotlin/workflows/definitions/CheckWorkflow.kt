package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object CheckWorkflow : ProjectWorkflow("check.yml") {

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val checkCommand = input(
        "check-command",
        description = "Command to run for checking",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupConfigurable {
        override var setupAction by inputProp(CheckWorkflow.setupAction)
        override var setupParams by inputProp(CheckWorkflow.setupParams)
        var checkCommand by inputRefProp(CheckWorkflow.checkCommand)
    }
}

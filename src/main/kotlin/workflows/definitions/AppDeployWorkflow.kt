package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.stringInput
import dsl.refInput
import workflows.ProjectWorkflow

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml") {

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go, python",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val deployCommand = input(
        "deploy-command",
        description = "Command to run for deployment",
        required = true
    )
    val tag = input(
        "tag",
        description = "Tag/version to deploy (checked out at this ref)",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(AppDeployWorkflow), SetupConfigurable {
        override var setupAction by stringInput(AppDeployWorkflow.setupAction)
        override var setupParams by stringInput(AppDeployWorkflow.setupParams)
        var deployCommand by refInput(AppDeployWorkflow.deployCommand)
        var tag by refInput(AppDeployWorkflow.tag)
    }
}

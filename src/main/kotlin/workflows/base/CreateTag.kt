package workflows.base

import actions.CreateAppTokenAction
import actions.GithubTagAction
import dsl.CreateTagWorkflow
import dsl.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateCreateTag() {
    workflow(
        name = "Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = CreateTagWorkflow.inputs,
                secrets = CreateTagWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/create-tag.main.kts"),
        targetFileName = "create-tag.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
    ) {
        job(
            id = "create_tag",
            name = "Create Tag",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps(fetchDepth = "0")
            run(
                name = "Run validation",
                command = CreateTagWorkflow.checkCommand.ref,
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = CreateTagWorkflow.appId.ref,
                    appPrivateKey = CreateTagWorkflow.appPrivateKey.ref,
                ),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = CreateTagWorkflow.defaultBump.ref,
                    tagPrefix = CreateTagWorkflow.tagPrefix.ref,
                    releaseBranches = CreateTagWorkflow.releaseBranches.ref,
                ),
            )
        }
    }
}

package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.GRADLE_PORTAL_SECRETS
import shared.MAVEN_SONATYPE_SECRETS
import shared.SETUP_ACTION_INPUT
import shared.SETUP_PARAMS_INPUT
import shared.conditionalSetupSteps
import shared.stringInput
import shared.withRequired
import java.io.File

fun generatePublish(outputDir: File) {
    workflow(
        name = "Publish",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/publish.main.kts"),
        targetFileName = "publish.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        SETUP_ACTION_INPUT,
                        SETUP_PARAMS_INPUT,
                        "publish-command" to stringInput(
                            description = "Command to run for publishing",
                            required = true,
                        ),
                    ),
                    "secrets" to (MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS).withRequired(false),
                ),
            ),
            "permissions" to mapOf("contents" to "read"),
        ),
    ) {
        job(
            id = "publish",
            name = "Publish",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = "\${{ inputs.publish-command }}",
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                    "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
                    "ORG_GRADLE_PROJECT_signingKeyId" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                    "ORG_GRADLE_PROJECT_signingPublicKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingPassword" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
                    "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                    "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                ),
            )
        }
    }
}

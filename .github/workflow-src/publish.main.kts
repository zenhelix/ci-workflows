#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Publish",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "publish.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    "setup-params" to stringInput(
                        description = "JSON object with setup parameters",
                        default = "{}",
                    ),
                    "publish-command" to stringInput(
                        description = "Command to run for publishing",
                        required = true,
                    ),
                ),
                "secrets" to mapOf(
                    "GRADLE_PUBLISH_KEY" to secretInput("Gradle Plugin Portal publish key", required = false),
                    "GRADLE_PUBLISH_SECRET" to secretInput("Gradle Plugin Portal publish secret", required = false),
                    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("GPG signing key ID", required = false),
                    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("GPG signing public key (ASCII armored)", required = false),
                    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("GPG signing private key (ASCII armored)", required = false),
                    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("GPG signing key passphrase", required = false),
                    "MAVEN_SONATYPE_USERNAME" to secretInput("Maven Central (Sonatype) username", required = false),
                    "MAVEN_SONATYPE_TOKEN" to secretInput("Maven Central (Sonatype) token", required = false),
                ),
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

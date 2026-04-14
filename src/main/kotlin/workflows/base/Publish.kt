package workflows.base

import workflows.definitions.PublishWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generatePublish() {
    workflow(
        name = "Publish",
        on = listOf(
            WorkflowCall(
                inputs = PublishWorkflow.inputs,
                secrets = PublishWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/publish.main.kts"),
        targetFileName = "publish.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Read),
    ) {
        job(
            id = "publish",
            name = "Publish",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = PublishWorkflow.publishCommand.ref.expression,
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to PublishWorkflow.gradlePublishKey.ref.expression,
                    "GRADLE_PUBLISH_SECRET" to PublishWorkflow.gradlePublishSecret.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKeyId" to PublishWorkflow.mavenSonatypeSigningKeyId.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKey" to PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPassword" to PublishWorkflow.mavenSonatypeSigningPassword.ref.expression,
                    "MAVEN_SONATYPE_USERNAME" to PublishWorkflow.mavenSonatypeUsername.ref.expression,
                    "MAVEN_SONATYPE_TOKEN" to PublishWorkflow.mavenSonatypeToken.ref.expression,
                ),
            )
        }
    }
}

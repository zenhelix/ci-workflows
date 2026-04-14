package workflows

import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.stringInput
import dsl.refInput
import workflows.core.ProjectWorkflow
import workflows.helpers.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object PublishWorkflow : ProjectWorkflow("publish.yml") {

    val setupAction = input(
        "setup-action",
        description = SetupConfigurable.SETUP_ACTION_DESCRIPTION,
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = SetupConfigurable.SETUP_PARAMS_DESCRIPTION,
        default = SetupConfigurable.SETUP_PARAMS_DEFAULT
    )
    val publishCommand = input(
        "publish-command",
        description = "Command to run for publishing",
        required = true
    )
    val mavenSonatypeUsername = secret(
        "MAVEN_SONATYPE_USERNAME",
        description = "Maven Central (Sonatype) username", required = false
    )
    val mavenSonatypeToken = secret(
        "MAVEN_SONATYPE_TOKEN",
        description = "Maven Central (Sonatype) token", required = false
    )
    val mavenSonatypeSigningKeyId = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ID",
        description = "GPG signing key ID", required = false
    )
    val mavenSonatypeSigningPubKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED",
        description = "GPG signing public key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED",
        description = "GPG signing private key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningPassword = secret(
        "MAVEN_SONATYPE_SIGNING_PASSWORD",
        description = "GPG signing key passphrase", required = false
    )
    val gradlePublishKey = secret(
        "GRADLE_PUBLISH_KEY",
        description = "Gradle Plugin Portal publish key", required = false
    )
    val gradlePublishSecret = secret(
        "GRADLE_PUBLISH_SECRET",
        description = "Gradle Plugin Portal publish secret", required = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow), SetupConfigurable {
        var setupAction by stringInput(PublishWorkflow.setupAction)
        var setupParams by stringInput(PublishWorkflow.setupParams)
        var publishCommand by refInput(PublishWorkflow.publishCommand)

        override fun applySetup(action: String, params: String) {
            setupAction = action
            setupParams = params
        }
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    fun generate() {
        workflow(
            name = "Publish",
            on = listOf(
                WorkflowCall(
                    inputs = inputs,
                    secrets = secrets,
                ),
            ),
            sourceFile = File("src/main/kotlin/workflows/PublishWorkflow.kt"),
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
                    command = publishCommand.ref.expression,
                    env = linkedMapOf(
                        "GRADLE_PUBLISH_KEY" to gradlePublishKey.ref.expression,
                        "GRADLE_PUBLISH_SECRET" to gradlePublishSecret.ref.expression,
                        "ORG_GRADLE_PROJECT_signingKeyId" to mavenSonatypeSigningKeyId.ref.expression,
                        "ORG_GRADLE_PROJECT_signingPublicKey" to mavenSonatypeSigningPubKeyAsciiArmored.ref.expression,
                        "ORG_GRADLE_PROJECT_signingKey" to mavenSonatypeSigningKeyAsciiArmored.ref.expression,
                        "ORG_GRADLE_PROJECT_signingPassword" to mavenSonatypeSigningPassword.ref.expression,
                        "MAVEN_SONATYPE_USERNAME" to mavenSonatypeUsername.ref.expression,
                        "MAVEN_SONATYPE_TOKEN" to mavenSonatypeToken.ref.expression,
                    ),
                )
            }
        }
    }
}

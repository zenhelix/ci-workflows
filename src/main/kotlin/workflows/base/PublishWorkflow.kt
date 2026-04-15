package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import dsl.core.expr
import workflows.support.conditionalSetupSteps

object PublishWorkflow : ProjectWorkflow("publish.yml", "Publish"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val publishCommand = input("publish-command", "Command to run for publishing", required = true)
    val mavenSonatypeUsername = secret("MAVEN_SONATYPE_USERNAME", "Maven Central (Sonatype) username", required = false)
    val mavenSonatypeToken = secret("MAVEN_SONATYPE_TOKEN", "Maven Central (Sonatype) token", required = false)
    val mavenSonatypeSigningKeyId = secret("MAVEN_SONATYPE_SIGNING_KEY_ID", "GPG signing key ID", required = false)
    val mavenSonatypeSigningPubKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED", "GPG signing public key (ASCII armored)", required = false)
    val mavenSonatypeSigningKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED", "GPG signing private key (ASCII armored)", required = false)
    val mavenSonatypeSigningPassword = secret("MAVEN_SONATYPE_SIGNING_PASSWORD", "GPG signing key passphrase", required = false)
    val gradlePublishKey = secret("GRADLE_PUBLISH_KEY", "Gradle Plugin Portal publish key", required = false)
    val gradlePublishSecret = secret("GRADLE_PUBLISH_SECRET", "Gradle Plugin Portal publish secret", required = false)

    val mavenSecrets = listOf(
        mavenSonatypeUsername, mavenSonatypeToken,
        mavenSonatypeSigningKeyId, mavenSonatypeSigningPubKeyAsciiArmored,
        mavenSonatypeSigningKeyAsciiArmored, mavenSonatypeSigningPassword,
    )
    val gradlePortalSecrets = listOf(gradlePublishKey, gradlePublishSecret)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<PublishWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@PublishWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "publish", name = "Publish", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = publishCommand.expr,
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to gradlePublishKey.expr,
                    "GRADLE_PUBLISH_SECRET" to gradlePublishSecret.expr,
                    "ORG_GRADLE_PROJECT_signingKeyId" to mavenSonatypeSigningKeyId.expr,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to mavenSonatypeSigningPubKeyAsciiArmored.expr,
                    "ORG_GRADLE_PROJECT_signingKey" to mavenSonatypeSigningKeyAsciiArmored.expr,
                    "ORG_GRADLE_PROJECT_signingPassword" to mavenSonatypeSigningPassword.expr,
                    "MAVEN_SONATYPE_USERNAME" to mavenSonatypeUsername.expr,
                    "MAVEN_SONATYPE_TOKEN" to mavenSonatypeToken.expr,
                ),
            )
        }
    }
}

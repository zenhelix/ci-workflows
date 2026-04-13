package config

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

fun Map<String, WorkflowCall.Secret>.passthrough(): Map<String, String> =
    keys.associateWith { "\${{ secrets.$it }}" }

val MAVEN_SONATYPE_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to WorkflowCall.Secret("Maven Central (Sonatype) username", true),
    "MAVEN_SONATYPE_TOKEN" to WorkflowCall.Secret("Maven Central (Sonatype) token", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to WorkflowCall.Secret("GPG signing key ID", true),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing public key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing private key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to WorkflowCall.Secret("GPG signing key passphrase", true),
)

val GRADLE_PORTAL_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to WorkflowCall.Secret("Gradle Plugin Portal publish key", true),
    "GRADLE_PUBLISH_SECRET" to WorkflowCall.Secret("Gradle Plugin Portal publish secret", true),
)

val APP_SECRETS = mapOf(
    "app-id" to WorkflowCall.Secret("GitHub App ID for generating commit token", true),
    "app-private-key" to WorkflowCall.Secret("GitHub App private key for generating commit token", true),
)

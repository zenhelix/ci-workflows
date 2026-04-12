#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.Mode.Read
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "Gradle Publish",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "java-version" to WorkflowCall.Input(
                    description = "JDK version to use",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "17"
                ),
                "publish-command" to WorkflowCall.Input(
                    description = "Gradle publish command (e.g. './gradlew publish', './gradlew publishPlugins')",
                    required = true,
                    type = WorkflowCall.Type.String
                )
            ),
            secrets = mapOf(
                "GRADLE_PUBLISH_KEY" to WorkflowCall.Secret(
                    description = "Gradle Plugin Portal publish key",
                    required = false
                ),
                "GRADLE_PUBLISH_SECRET" to WorkflowCall.Secret(
                    description = "Gradle Plugin Portal publish secret",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_KEY_ID" to WorkflowCall.Secret(
                    description = "GPG signing key ID",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to WorkflowCall.Secret(
                    description = "GPG signing public key (ASCII armored)",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to WorkflowCall.Secret(
                    description = "GPG signing private key (ASCII armored)",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_PASSWORD" to WorkflowCall.Secret(
                    description = "GPG signing key passphrase",
                    required = false
                ),
                "MAVEN_SONATYPE_USERNAME" to WorkflowCall.Secret(
                    description = "Maven Central (Sonatype) username",
                    required = false
                ),
                "MAVEN_SONATYPE_TOKEN" to WorkflowCall.Secret(
                    description = "Maven Central (Sonatype) token",
                    required = false
                )
            )
        )
    ),
    permissions = mapOf(Contents to Read),
    sourceFile = __FILE__,
    targetFileName = "release-publish-gradle.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "publish", name = "Publish", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout())
        uses(
            name = "Set up Java",
            action = SetupJava(
                javaVersion = expr { "inputs.java-version" },
                distribution = Temurin
            )
        )
        uses(
            name = "Setup Gradle",
            action = ActionsSetupGradle(gradleHomeCacheCleanup = true)
        )
        run(
            name = "Validate secrets",
            command = """
                if [ -z "${'$'}GRADLE_PUBLISH_KEY" ] && [ -z "${'$'}SIGNING_KEY_ID" ]; then
                  echo "::error::No publishing credentials configured. Set either GRADLE_PUBLISH_KEY/SECRET or MAVEN_SONATYPE_SIGNING_* secrets."
                  exit 1
                fi
            """.trimIndent(),
            env = mapOf(
                "GRADLE_PUBLISH_KEY" to expr { "secrets.GRADLE_PUBLISH_KEY" },
                "SIGNING_KEY_ID" to expr { "secrets.MAVEN_SONATYPE_SIGNING_KEY_ID" }
            )
        )
        run(
            name = "Publish",
            command = expr { "inputs.publish-command" },
            env = mapOf(
                "GRADLE_PUBLISH_KEY" to expr { "secrets.GRADLE_PUBLISH_KEY" },
                "GRADLE_PUBLISH_SECRET" to expr { "secrets.GRADLE_PUBLISH_SECRET" },
                "ORG_GRADLE_PROJECT_signingKeyId" to expr { "secrets.MAVEN_SONATYPE_SIGNING_KEY_ID" },
                "ORG_GRADLE_PROJECT_signingPublicKey" to expr { "secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" },
                "ORG_GRADLE_PROJECT_signingKey" to expr { "secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" },
                "ORG_GRADLE_PROJECT_signingPassword" to expr { "secrets.MAVEN_SONATYPE_SIGNING_PASSWORD" },
                "MAVEN_SONATYPE_USERNAME" to expr { "secrets.MAVEN_SONATYPE_USERNAME" },
                "MAVEN_SONATYPE_TOKEN" to expr { "secrets.MAVEN_SONATYPE_TOKEN" }
            )
        )
    }
}

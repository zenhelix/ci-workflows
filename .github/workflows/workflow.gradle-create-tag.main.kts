#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")
@file:DependsOn("actions:create-github-app-token:v2")
@file:DependsOn("mathieudutour:github-tag-action:v6.2")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.Checkout.FetchDepth
import io.github.typesafegithub.workflows.actions.actions.CreateGithubAppToken
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.actions.mathieudutour.GithubTagAction_Untyped
import io.github.typesafegithub.workflows.domain.Mode.Write
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
    name = "Gradle Create Tag",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "java-version" to WorkflowCall.Input(
                    description = "JDK version to use",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "17"
                ),
                "gradle-command" to WorkflowCall.Input(
                    description = "Gradle command to run",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "./gradlew check"
                ),
                "default-bump" to WorkflowCall.Input(
                    description = "Default version bump type (major, minor, patch)",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "patch"
                ),
                "tag-prefix" to WorkflowCall.Input(
                    description = "Prefix for the tag (e.g. 'v')",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = ""
                ),
                "release-branches" to WorkflowCall.Input(
                    description = "Comma-separated list of branch patterns for releases",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "main,[0-9]+\\.x"
                )
            ),
            secrets = mapOf(
                "app-id" to WorkflowCall.Secret(
                    description = "GitHub App ID for generating commit token",
                    required = true
                ),
                "app-private-key" to WorkflowCall.Secret(
                    description = "GitHub App private key for generating commit token",
                    required = true
                )
            )
        )
    ),
    permissions = mapOf(Contents to Write),
    sourceFile = __FILE__,
    targetFileName = "gradle-create-tag.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "create_tag", name = "Create Tag", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout(fetchDepth = FetchDepth.Value(0)))
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
        run(name = "Check", command = expr { "inputs.gradle-command" })
        val token = uses(
            name = "Generate App Token",
            action = CreateGithubAppToken(
                appId = expr { "secrets.app-id" },
                privateKey = expr { "secrets.app-private-key" }
            )
        )
        uses(
            name = "Bump version and push tag",
            action = GithubTagAction_Untyped(
                githubToken_Untyped = expr { token.outputs["token"] },
                defaultBump_Untyped = expr { "inputs.default-bump" },
                tagPrefix_Untyped = expr { "inputs.tag-prefix" },
                releaseBranches_Untyped = expr { "inputs.release-branches" }
            )
        )
    }
}

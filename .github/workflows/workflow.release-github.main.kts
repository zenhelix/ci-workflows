#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("softprops:action-gh-release:v2")
@file:DependsOn("mikepenz:release-changelog-builder-action:v6")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.Checkout.FetchDepth
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
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
    name = "GitHub Release",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "changelog-config" to WorkflowCall.Input(
                    description = "Path to changelog configuration file",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = ".github/changelog-config.json"
                )
            )
        )
    ),
    permissions = mapOf(Contents to Write),
    sourceFile = __FILE__,
    targetFileName = "release-github.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "release", name = "GitHub Release", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout(fetchDepth = FetchDepth.Value(0)))

        val tag = expr { github.ref_name }

        val changelogBuilder = uses(
            name = "Build Changelog",
            action = ReleaseChangelogBuilderAction_Untyped(
                configuration_Untyped = expr { "inputs.changelog-config" },
                toTag_Untyped = tag
            ),
            env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN })
        )

        uses(
            name = "Create Release",
            action = ActionGhRelease(
                tagName = tag,
                name = tag,
                body = expr { changelogBuilder.outputs["changelog"] },
                draft = false
            ),
            env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN })
        )
    }
}

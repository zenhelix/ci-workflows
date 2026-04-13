#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Kotlin Library Release",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = "kotlin-library-release.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(
                        description = "JDK version to use",
                        default = DEFAULT_JAVA_VERSION,
                    ),
                    "publish-command" to stringInput(
                        description = "Gradle publish command for Maven Central",
                        required = true,
                    ),
                    "changelog-config" to stringInput(
                        description = "Path to changelog configuration file",
                        default = DEFAULT_CHANGELOG_CONFIG,
                    ),
                ),
                "secrets" to mapOf(
                    "MAVEN_SONATYPE_USERNAME" to secretInput("MAVEN_SONATYPE_USERNAME"),
                    "MAVEN_SONATYPE_TOKEN" to secretInput("MAVEN_SONATYPE_TOKEN"),
                    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("MAVEN_SONATYPE_SIGNING_KEY_ID"),
                    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED"),
                    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED"),
                    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("MAVEN_SONATYPE_SIGNING_PASSWORD"),
                ),
            ),
        ),
    ),
) {
    job(
        id = "release",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("release.yml"),
            "with" to mapOf(
                "changelog-config" to "\${{ inputs.changelog-config }}",
            ),
        ),
    ) {
        run(name = "noop", command = "true")
    }

    job(
        id = "publish",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "needs" to "release",
            "uses" to reusableWorkflow("publish.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to "{\"java-version\": \"\${{ inputs.java-version }}\"}",
                "publish-command" to "\${{ inputs.publish-command }}",
            ),
            "secrets" to mapOf(
                "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ID" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_PASSWORD" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
            ),
        ),
    ) {
        run(name = "noop", command = "true")
    }
}

cleanReusableWorkflowJobs("kotlin-library-release.yml")

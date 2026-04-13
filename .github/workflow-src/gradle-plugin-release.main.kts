#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val targetFile = "gradle-plugin-release.yml"

workflow(
    name = "Gradle Plugin Release",
    on = listOf(WorkflowDispatch()),
    sourceFile = __FILE__,
    targetFileName = targetFile,
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
                        description = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
                        required = true,
                    ),
                    "changelog-config" to stringInput(
                        description = "Path to changelog configuration file",
                        default = DEFAULT_CHANGELOG_CONFIG,
                    ),
                ),
                "secrets" to mapOf(
                    "MAVEN_SONATYPE_USERNAME" to secretInput("", required = true),
                    "MAVEN_SONATYPE_TOKEN" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("", required = true),
                    "GRADLE_PUBLISH_KEY" to secretInput("", required = true),
                    "GRADLE_PUBLISH_SECRET" to secretInput("", required = true),
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
        run(name = "placeholder", command = "echo placeholder")
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
                "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
            ),
        ),
    ) {
        run(name = "placeholder", command = "echo placeholder")
    }
}

cleanReusableWorkflowJobs(targetFile)

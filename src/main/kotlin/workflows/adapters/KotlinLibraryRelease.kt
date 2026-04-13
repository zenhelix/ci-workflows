package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_CHANGELOG_CONFIG
import shared.DEFAULT_JAVA_VERSION
import shared.MAVEN_SONATYPE_SECRETS
import shared.MAVEN_SONATYPE_SECRETS_PASSTHROUGH
import shared.cleanReusableWorkflowJobs
import shared.noop
import shared.reusableWorkflow
import shared.stringInput
import java.io.File

fun generateKotlinLibraryRelease(outputDir: File) {
    val targetFile = "kotlin-library-release.yml"

    workflow(
        name = "Kotlin Library Release",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/kotlin-library-release.main.kts"),
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
                            description = "Gradle publish command for Maven Central",
                            required = true,
                        ),
                        "changelog-config" to stringInput(
                            description = "Path to changelog configuration file",
                            default = DEFAULT_CHANGELOG_CONFIG,
                        ),
                    ),
                    "secrets" to MAVEN_SONATYPE_SECRETS,
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
            noop()
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
                "secrets" to MAVEN_SONATYPE_SECRETS_PASSTHROUGH,
            ),
        ) {
            noop()
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

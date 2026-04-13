package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.MAVEN_SONATYPE_SECRETS
import config.MAVEN_SONATYPE_SECRETS_PASSTHROUGH
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateKotlinLibraryRelease(outputDir: File) {
    val targetFile = "kotlin-library-release.yml"

    workflow(
        name = "Kotlin Library Release",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "publish-command" to WorkflowCall.Input("Gradle publish command for Maven Central", true, WorkflowCall.Type.String),
                    "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                ),
                secrets = MAVEN_SONATYPE_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/kotlin-library-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
        }

        reusableWorkflowJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction("gradle")
            PublishWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            PublishWorkflow.publishCommand("\${{ inputs.publish-command }}")
            secrets(MAVEN_SONATYPE_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

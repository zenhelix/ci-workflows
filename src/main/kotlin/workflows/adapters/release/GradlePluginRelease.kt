package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.GRADLE_PORTAL_SECRETS
import config.MAVEN_SONATYPE_SECRETS
import config.passthrough
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateGradlePluginRelease(outputDir: File) {
    val targetFile = "gradle-plugin-release.yml"

    workflow(
        name = "Gradle Plugin Release",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "publish-command" to WorkflowCall.Input("Gradle publish command (publishes to both Maven Central and Gradle Portal)", true, WorkflowCall.Type.String),
                    "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                ),
                secrets = MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-plugin-release.main.kts"),
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
            secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

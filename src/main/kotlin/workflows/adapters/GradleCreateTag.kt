package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_JAVA_VERSION
import shared.DEFAULT_RELEASE_BRANCHES
import shared.cleanReusableWorkflowJobs
import shared.dsl.CreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradleCreateTag(outputDir: File) {
    val targetFile = "gradle-create-tag.yml"

    workflow(
        name = "Gradle Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "gradle-command" to WorkflowCall.Input("Gradle validation command", false, WorkflowCall.Type.String, "./gradlew check"),
                    "default-bump" to WorkflowCall.Input("Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, ""),
                    "release-branches" to WorkflowCall.Input("Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction("gradle")
            CreateTagWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            CreateTagWorkflow.checkCommand("\${{ inputs.gradle-command }}")
            CreateTagWorkflow.defaultBump("\${{ inputs.default-bump }}")
            CreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            CreateTagWorkflow.releaseBranches("\${{ inputs.release-branches }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

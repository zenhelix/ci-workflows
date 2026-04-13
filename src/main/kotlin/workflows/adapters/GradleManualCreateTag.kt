package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_JAVA_VERSION
import shared.cleanReusableWorkflowJobs
import shared.dsl.ManualCreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradleManualCreateTag(outputDir: File) {
    val targetFile = "gradle-manual-create-tag.yml"

    workflow(
        name = "Gradle Manual Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String),
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "gradle-command" to WorkflowCall.Input("Gradle validation command", false, WorkflowCall.Type.String, "./gradlew check"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, ""),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion("\${{ inputs.tag-version }}")
            ManualCreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            ManualCreateTagWorkflow.setupAction("gradle")
            ManualCreateTagWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            ManualCreateTagWorkflow.checkCommand("\${{ inputs.gradle-command }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.ManualCreateTagWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.inputRef
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateGradleManualCreateTag(outputDir: File) {
    val targetFile = "gradle-manual-create-tag.yml"

    workflow(
        name = "Gradle Manual Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    CommonInputs.tagVersion(),
                    CommonInputs.javaVersion(),
                    CommonInputs.gradleCommand(description = "Gradle validation command"),
                    CommonInputs.tagPrefix(),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
            ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
            ManualCreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
            ManualCreateTagWorkflow.checkCommand(inputRef("gradle-command"))
            secrets(APP_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

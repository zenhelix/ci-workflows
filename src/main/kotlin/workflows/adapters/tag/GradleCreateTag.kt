package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.CreateTagWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.inputRef
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateGradleCreateTag(outputDir: File) {
    val targetFile = "gradle-create-tag.yml"

    workflow(
        name = "Gradle Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    CommonInputs.javaVersion(),
                    CommonInputs.gradleCommand(description = "Gradle validation command"),
                    CommonInputs.defaultBump(),
                    CommonInputs.tagPrefix(),
                    CommonInputs.releaseBranches(),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
            CreateTagWorkflow.checkCommand(inputRef("gradle-command"))
            CreateTagWorkflow.defaultBump(inputRef("default-bump"))
            CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
            CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
            secrets(APP_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

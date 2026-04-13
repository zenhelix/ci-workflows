package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.ManualCreateTagWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGradleManualCreateTag(outputDir: File) {
    generateAdapterWorkflow(
        name = "Gradle Manual Create Tag",
        sourceFileSlug = "gradle-manual-create-tag",
        targetFileName = "gradle-manual-create-tag.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.tagVersion(),
                CommonInputs.javaVersion(),
                CommonInputs.gradleCommand(description = "Gradle validation command"),
                CommonInputs.tagPrefix(),
            ),
            secrets = APP_SECRETS,
        ),
        jobs = listOf(
            reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
                ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
                ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
                ManualCreateTagWorkflow.setupAction(SetupTool.Gradle.id)
                ManualCreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
                ManualCreateTagWorkflow.checkCommand(inputRef("gradle-command"))
                secrets(APP_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

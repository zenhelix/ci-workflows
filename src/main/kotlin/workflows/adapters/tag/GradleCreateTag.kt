package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.CreateTagWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGradleCreateTag(outputDir: File) {
    generateAdapterWorkflow(
        name = "Gradle Create Tag",
        sourceFileSlug = "gradle-create-tag",
        targetFileName = "gradle-create-tag.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.gradleCommand(description = "Gradle validation command"),
                CommonInputs.defaultBump(),
                CommonInputs.tagPrefix(),
                CommonInputs.releaseBranches(),
            ),
            secrets = APP_SECRETS,
        ),
        jobs = listOf(
            reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
                CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
                CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
                CreateTagWorkflow.checkCommand(inputRef("gradle-command"))
                CreateTagWorkflow.defaultBump(inputRef("default-bump"))
                CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
                CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
                secrets(APP_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

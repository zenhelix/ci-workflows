package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleManualCreateTagAdapter : AdapterWorkflow("gradle-manual-create-tag.yml") {
    override val workflowName = "Gradle Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")

    init { secrets(APP_SECRETS) }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(tagVersion.ref)
            ManualCreateTagWorkflow.tagPrefix(tagPrefix.ref)
            ManualCreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            ManualCreateTagWorkflow.checkCommand(gradleCommand.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}

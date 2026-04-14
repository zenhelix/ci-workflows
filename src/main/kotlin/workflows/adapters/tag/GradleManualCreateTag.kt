package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.SetupTool
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

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ManualCreateTagWorkflow.JobBuilder>(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            tagVersion(this@GradleManualCreateTagAdapter.tagVersion.ref)
            tagPrefix(this@GradleManualCreateTagAdapter.tagPrefix.ref)
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            checkCommand(gradleCommand.ref)
            passthroughAllSecrets()
        },
    )
}

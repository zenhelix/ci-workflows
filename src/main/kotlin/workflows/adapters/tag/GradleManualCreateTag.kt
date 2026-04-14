package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ManualCreateTagWorkflow
import workflows.setup

object GradleManualCreateTagAdapter : AdapterWorkflow("gradle-manual-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Gradle Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@GradleManualCreateTagAdapter.tagVersion.ref.expression
            tagPrefix = this@GradleManualCreateTagAdapter.tagPrefix.ref.expression
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            checkCommand = gradleCommand.ref.expression
            passthroughAllSecrets()
        },
    )
}

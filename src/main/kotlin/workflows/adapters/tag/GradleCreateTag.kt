package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.CreateTagWorkflow
import workflows.setup

object GradleCreateTagAdapter : AdapterWorkflow("gradle-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Gradle Create Tag"

    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            checkCommand = gradleCommand.ref.expression
            defaultBump = this@GradleCreateTagAdapter.defaultBump.ref.expression
            tagPrefix = this@GradleCreateTagAdapter.tagPrefix.ref.expression
            releaseBranches = this@GradleCreateTagAdapter.releaseBranches.ref.expression
            passthroughAllSecrets()
        },
    )
}

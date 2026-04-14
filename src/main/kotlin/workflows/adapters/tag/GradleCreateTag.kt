package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.definitions.CreateTagWorkflow
import workflows.ProjectAdapterWorkflow
import workflows.setup

object GradleCreateTagAdapter : ProjectAdapterWorkflow("gradle-create-tag.yml") {
    override val workflowName = "Gradle Create Tag"

    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            checkCommand = this@GradleCreateTagAdapter.gradleCommand.ref
            defaultBump = this@GradleCreateTagAdapter.defaultBump.ref
            tagPrefix = this@GradleCreateTagAdapter.tagPrefix.ref
            releaseBranches = this@GradleCreateTagAdapter.releaseBranches.ref
            passthroughAllSecrets()
        },
    )
}

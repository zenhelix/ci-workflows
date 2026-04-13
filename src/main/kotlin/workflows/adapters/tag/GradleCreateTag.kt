package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleCreateTagAdapter : AdapterWorkflow("gradle-create-tag.yml") {
    override val workflowName = "Gradle Create Tag"

    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    init { secrets(APP_SECRETS) }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            CreateTagWorkflow.checkCommand(gradleCommand.ref)
            CreateTagWorkflow.defaultBump(defaultBump.ref)
            CreateTagWorkflow.tagPrefix(tagPrefix.ref)
            CreateTagWorkflow.releaseBranches(releaseBranches.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}

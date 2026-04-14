package workflows.definitions

import config.DEFAULT_RELEASE_BRANCHES
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.stringInput
import dsl.refInput
import workflows.ProjectWorkflow

object CreateTagWorkflow : ProjectWorkflow("create-tag.yml") {

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val checkCommand = input(
        "check-command",
        description = "Validation command to run before tagging",
        required = true
    )
    val defaultBump = input(
        "default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch"
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
    val releaseBranches = input(
        "release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES
    )
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow), SetupConfigurable {
        override var setupAction by stringInput(CreateTagWorkflow.setupAction)
        override var setupParams by stringInput(CreateTagWorkflow.setupParams)
        var checkCommand by refInput(CreateTagWorkflow.checkCommand)
        var defaultBump by refInput(CreateTagWorkflow.defaultBump)
        var tagPrefix by refInput(CreateTagWorkflow.tagPrefix)
        var releaseBranches by refInput(CreateTagWorkflow.releaseBranches)
    }
}

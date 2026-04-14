package workflows.definitions

import config.DEFAULT_RELEASE_BRANCHES
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
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
        override var setupAction by inputProp(CreateTagWorkflow.setupAction)
        override var setupParams by inputProp(CreateTagWorkflow.setupParams)
        var checkCommand by inputRefProp(CreateTagWorkflow.checkCommand)
        var defaultBump by inputRefProp(CreateTagWorkflow.defaultBump)
        var tagPrefix by inputRefProp(CreateTagWorkflow.tagPrefix)
        var releaseBranches by inputRefProp(CreateTagWorkflow.releaseBranches)
    }
}

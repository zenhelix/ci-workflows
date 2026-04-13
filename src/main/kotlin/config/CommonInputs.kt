package config

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

object CommonInputs {
    fun javaVersion(default: String = DEFAULT_JAVA_VERSION) =
        "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, default)

    fun javaVersions() =
        "java-versions" to WorkflowCall.Input(
            "JSON array of JDK versions for matrix build (overrides java-version)",
            false, WorkflowCall.Type.String, "")

    fun gradleCommand(default: String = "./gradlew check") =
        "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, default)

    fun publishCommand(description: String = "Gradle publish command") =
        "publish-command" to WorkflowCall.Input(description, true, WorkflowCall.Type.String)

    fun changelogConfig() =
        "changelog-config" to WorkflowCall.Input(
            "Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG)

    fun goVersion(default: String = DEFAULT_GO_VERSION) =
        "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, default)

    fun tagVersion() =
        "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String)

    fun defaultBump() =
        "default-bump" to WorkflowCall.Input(
            "Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch")

    fun tagPrefix(default: String = "") =
        "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, default)

    fun releaseBranches() =
        "release-branches" to WorkflowCall.Input(
            "Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES)

    fun checkCommand(description: String = "Validation command", default: String) =
        "check-command" to WorkflowCall.Input(description, false, WorkflowCall.Type.String, default)

    fun deployCommand() =
        "deploy-command" to WorkflowCall.Input("Command to run for deployment", true, WorkflowCall.Type.String)

    fun setupAction() =
        "setup-action" to WorkflowCall.Input("Setup action to use: gradle, go, python", true, WorkflowCall.Type.String)

    fun setupParams() =
        "setup-params" to WorkflowCall.Input(
            "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})", false, WorkflowCall.Type.String, "{}")

    fun tag() =
        "tag" to WorkflowCall.Input("Tag/version to deploy (checked out at this ref)", true, WorkflowCall.Type.String)
}

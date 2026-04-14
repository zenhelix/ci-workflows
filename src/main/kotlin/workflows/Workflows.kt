package workflows

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_RELEASE_BRANCHES
import config.reusableWorkflow
import dsl.ReusableWorkflow
import dsl.ReusableWorkflowJobBuilder
import dsl.inputProp

object CheckWorkflow : ReusableWorkflow("check.yml") {
    override val usesString = reusableWorkflow(fileName)

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
        description = "Command to run for checking",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
        var setupAction by inputProp(CheckWorkflow.setupAction)
        var setupParams by inputProp(CheckWorkflow.setupParams)
        var checkCommand by inputProp(CheckWorkflow.checkCommand)
    }
}

object ConventionalCommitCheckWorkflow : ReusableWorkflow("conventional-commit-check.yml") {
    override val usesString = reusableWorkflow(fileName)

    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by inputProp(ConventionalCommitCheckWorkflow.allowedTypes)
    }
}

object CreateTagWorkflow : ReusableWorkflow("create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)

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

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow) {
        var setupAction by inputProp(CreateTagWorkflow.setupAction)
        var setupParams by inputProp(CreateTagWorkflow.setupParams)
        var checkCommand by inputProp(CreateTagWorkflow.checkCommand)
        var defaultBump by inputProp(CreateTagWorkflow.defaultBump)
        var tagPrefix by inputProp(CreateTagWorkflow.tagPrefix)
        var releaseBranches by inputProp(CreateTagWorkflow.releaseBranches)
    }
}

object ManualCreateTagWorkflow : ReusableWorkflow("manual-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)

    val tagVersion = input(
        "tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go, python",
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
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow) {
        var tagVersion by inputProp(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by inputProp(ManualCreateTagWorkflow.tagPrefix)
        var setupAction by inputProp(ManualCreateTagWorkflow.setupAction)
        var setupParams by inputProp(ManualCreateTagWorkflow.setupParams)
        var checkCommand by inputProp(ManualCreateTagWorkflow.checkCommand)
    }
}

object ReleaseWorkflow : ReusableWorkflow("release.yml") {
    override val usesString = reusableWorkflow(fileName)

    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG
    )
    val draft = booleanInput(
        "draft",
        description = "Create release as draft",
        default = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        var changelogConfig by inputProp(ReleaseWorkflow.changelogConfig)
        var draft by inputProp(ReleaseWorkflow.draft)
    }
}

object PublishWorkflow : ReusableWorkflow("publish.yml") {
    override val usesString = reusableWorkflow(fileName)

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
    val publishCommand = input(
        "publish-command",
        description = "Command to run for publishing",
        required = true
    )
    val mavenSonatypeUsername = secret(
        "MAVEN_SONATYPE_USERNAME",
        description = "Maven Central (Sonatype) username", required = false
    )
    val mavenSonatypeToken = secret(
        "MAVEN_SONATYPE_TOKEN",
        description = "Maven Central (Sonatype) token", required = false
    )
    val mavenSonatypeSigningKeyId = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ID",
        description = "GPG signing key ID", required = false
    )
    val mavenSonatypeSigningPubKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED",
        description = "GPG signing public key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED",
        description = "GPG signing private key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningPassword = secret(
        "MAVEN_SONATYPE_SIGNING_PASSWORD",
        description = "GPG signing key passphrase", required = false
    )
    val gradlePublishKey = secret(
        "GRADLE_PUBLISH_KEY",
        description = "Gradle Plugin Portal publish key", required = false
    )
    val gradlePublishSecret = secret(
        "GRADLE_PUBLISH_SECRET",
        description = "Gradle Plugin Portal publish secret", required = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow) {
        var setupAction by inputProp(PublishWorkflow.setupAction)
        var setupParams by inputProp(PublishWorkflow.setupParams)
        var publishCommand by inputProp(PublishWorkflow.publishCommand)
    }
}

object LabelerWorkflow : ReusableWorkflow("labeler.yml") {
    override val usesString = reusableWorkflow(fileName)

    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by inputProp(LabelerWorkflow.configPath)
    }
}

#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")

import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.actions.Action
import io.github.typesafegithub.workflows.dsl.JobBuilder

// ── Constants ──────────────────────────────────────────────────────────────────

val UBUNTU_LATEST = RunnerType.UbuntuLatest

val DEFAULT_JAVA_VERSION = "17"
val DEFAULT_GO_VERSION = "1.22"
val DEFAULT_PYTHON_VERSION = "3.12"
val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

val WORKFLOW_REF = "v2"
val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"
val ACTION_BASE = "zenhelix/ci-workflows/.github/actions"

// ── Workflow Call Input Helpers ─────────────────────────────────────────────────

fun workflowCallInput(
    description: String,
    type: String,
    required: Boolean,
    default: Any? = null,
): Map<String, Any?> = buildMap {
    put("description", description)
    put("type", type)
    put("required", required)
    if (default != null) put("default", default)
}

fun stringInput(description: String, required: Boolean = false, default: String? = null) =
    workflowCallInput(description, "string", required, default)

fun booleanInput(description: String, required: Boolean = false, default: Boolean? = null) =
    workflowCallInput(description, "boolean", required, default)

fun secretInput(description: String, required: Boolean = true) = mapOf(
    "description" to description,
    "required" to required,
)

// ── Reusable Workflow Call Helpers ──────────────────────────────────────────────

fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"
fun localAction(name: String) = "$ACTION_BASE/$name@$WORKFLOW_REF"

// ── Action Classes ─────────────────────────────────────────────────────────────

class SetupGradleAction(
    private val javaVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-gradle")
    override fun toYamlArguments() = linkedMapOf(
        "java-version" to javaVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupGoAction(
    private val goVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-go")
    override fun toYamlArguments() = linkedMapOf(
        "go-version" to goVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupPythonAction(
    private val pythonVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-python")
    override fun toYamlArguments() = linkedMapOf(
        "python-version" to pythonVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class CreateAppTokenAction(
    private val appId: String,
    private val appPrivateKey: String,
) : Action<CreateAppTokenAction.CreateAppTokenOutputs>() {
    override val usesString = localAction("create-app-token")
    override fun toYamlArguments() = linkedMapOf(
        "app-id" to appId,
        "app-private-key" to appPrivateKey,
    )
    override fun buildOutputObject(stepId: String) = CreateAppTokenOutputs(stepId)

    class CreateAppTokenOutputs(stepId: String) : Action.Outputs(stepId) {
        val token: String get() = get("token")
    }
}

class GithubTagAction(
    private val githubToken: String,
    private val defaultBump: String,
    private val tagPrefix: String,
    private val releaseBranches: String,
) : Action<Action.Outputs>() {
    override val usesString = "mathieudutour/github-tag-action@v6.2"
    override fun toYamlArguments() = linkedMapOf(
        "github_token" to githubToken,
        "default_bump" to defaultBump,
        "tag_prefix" to tagPrefix,
        "release_branches" to releaseBranches,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class CheckoutAction(
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = "actions/checkout@v6"
    override fun toYamlArguments() = linkedMapOf<String, String>().apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class ReleaseChangelogBuilderAction(
    private val configuration: String,
    private val toTag: String,
) : Action<ReleaseChangelogBuilderAction.ChangelogOutputs>() {
    override val usesString = "mikepenz/release-changelog-builder-action@v6"
    override fun toYamlArguments() = linkedMapOf(
        "configuration" to configuration,
        "toTag" to toTag,
    )
    override fun buildOutputObject(stepId: String) = ChangelogOutputs(stepId)

    class ChangelogOutputs(stepId: String) : Action.Outputs(stepId) {
        val changelog: String get() = get("changelog")
    }
}

class GhReleaseAction(
    private val body: String,
    private val name: String,
    private val tagName: String,
    private val draft: String,
) : Action<Action.Outputs>() {
    override val usesString = "softprops/action-gh-release@v2"
    override fun toYamlArguments() = linkedMapOf(
        "body" to body,
        "name" to name,
        "tag_name" to tagName,
        "draft" to draft,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class LabelerAction(
    private val repoToken: String,
    private val configurationPath: String,
    private val syncLabels: String,
) : Action<Action.Outputs>() {
    override val usesString = "actions/labeler@v6"
    override fun toYamlArguments() = linkedMapOf(
        "repo-token" to repoToken,
        "configuration-path" to configurationPath,
        "sync-labels" to syncLabels,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

// ── DSL Helper Functions ───────────────────────────────────────────────────────

fun JobBuilder<*>.conditionalSetupSteps() {
    uses(
        name = "Setup Gradle",
        action = SetupGradleAction("\${{ fromJson(inputs.setup-params).java-version || '17' }}"),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupGoAction("\${{ fromJson(inputs.setup-params).go-version || '1.22' }}"),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupPythonAction("\${{ fromJson(inputs.setup-params).python-version || '3.12' }}"),
        condition = "inputs.setup-action == 'python'",
    )
}

fun JobBuilder<*>.conditionalSetupStepsFullHistory() {
    uses(
        name = "Setup Gradle",
        action = SetupGradleAction(
            javaVersion = "\${{ fromJson(inputs.setup-params).java-version || '17' }}",
            fetchDepth = "0",
        ),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupGoAction(
            goVersion = "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}",
            fetchDepth = "0",
        ),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupPythonAction(
            pythonVersion = "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}",
            fetchDepth = "0",
        ),
        condition = "inputs.setup-action == 'python'",
    )
}

// ── Setup Step Helpers (for _customArguments usage) ───────────────────────────

fun gradleSetupStep(
    javaVersionExpr: String = "fromJson(inputs.setup-params).java-version || '17'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Gradle",
    "if" to "inputs.setup-action == 'gradle'",
    "uses" to localAction("setup-gradle"),
    "with" to mapOf(
        "java-version" to "\${{ $javaVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun goSetupStep(
    goVersionExpr: String = "fromJson(inputs.setup-params).go-version || '1.22'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Go",
    "if" to "inputs.setup-action == 'go'",
    "uses" to localAction("setup-go"),
    "with" to mapOf(
        "go-version" to "\${{ $goVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun pythonSetupStep(
    pythonVersionExpr: String = "fromJson(inputs.setup-params).python-version || '3.12'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Python",
    "if" to "inputs.setup-action == 'python'",
    "uses" to localAction("setup-python"),
    "with" to mapOf(
        "python-version" to "\${{ $pythonVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun conditionalSetupStepsMap(fetchDepth: String = "1") = listOf(
    gradleSetupStep(fetchDepth = fetchDepth),
    goSetupStep(fetchDepth = fetchDepth),
    pythonSetupStep(fetchDepth = fetchDepth),
)

// ── Common Inputs ──────────────────────────────────────────────────────────────

val SETUP_ACTION_INPUT = "setup-action" to stringInput(
    description = "Setup action to use: gradle, go",
    required = true,
)

val SETUP_PARAMS_INPUT = "setup-params" to stringInput(
    description = "JSON object with setup parameters",
    default = "{}",
)

val CHECK_COMMAND_INPUT = "check-command" to stringInput(
    description = "Command to run for checking",
    required = true,
)

val APP_ID_SECRET = "app-id" to secretInput("GitHub App ID for generating commit token")
val APP_PRIVATE_KEY_SECRET = "app-private-key" to secretInput("GitHub App private key for generating commit token")

// ── Adapter Workflow Post-Processing ──────────────────────────────────────────

/**
 * Removes `runs-on` and `steps` blocks from jobs that use reusable workflows (`uses:` at job level).
 * GitHub Actions does not allow `runs-on` or `steps` on reusable workflow call jobs.
 */
fun cleanReusableWorkflowJobs(targetFileName: String) {
    val targetFile = java.io.File("../workflows/$targetFileName")
    val lines = targetFile.readLines()
    val output = mutableListOf<String>()

    val jobsLineIdx = lines.indexOfFirst { it.trimStart() == "jobs:" }
    val reusableJobIds = mutableSetOf<String>()

    if (jobsLineIdx >= 0) {
        var currentJobId: String? = null
        for (idx in (jobsLineIdx + 1) until lines.size) {
            val line = lines[idx]
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            if (indent == 0) break
            if (indent == 2 && line.trimEnd().endsWith(":")) {
                currentJobId = line.trim().removeSuffix(":")
            }
            if (indent == 4 && line.trimStart().startsWith("uses:") && currentJobId != null) {
                reusableJobIds.add(currentJobId)
            }
        }
    }

    var currentJobId2: String? = null
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val indent = if (line.isBlank()) -1 else line.length - line.trimStart().length

        if (indent == 2 && line.trimEnd().endsWith(":") && i > jobsLineIdx) {
            currentJobId2 = line.trim().removeSuffix(":")
        }

        val inReusable = currentJobId2 in reusableJobIds

        if (inReusable && indent == 4 && line.trimStart().startsWith("runs-on:")) {
            i++
            continue
        }

        if (inReusable && indent == 4 && line.trimStart().startsWith("steps:")) {
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                if (nextLine.isBlank()) { i++; continue }
                val nextIndent = nextLine.length - nextLine.trimStart().length
                if (nextIndent < 4) break
                if (nextIndent == 4 && !nextLine.trimStart().startsWith("-")) break
                i++
            }
            continue
        }

        output.add(line)
        i++
    }

    targetFile.writeText(output.joinToString("\n") + "\n")
}

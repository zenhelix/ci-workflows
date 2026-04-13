#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")

import io.github.typesafegithub.workflows.domain.RunnerType
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

// ── Setup Step Helpers ─────────────────────────────────────────────────────────

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

fun conditionalSetupSteps(fetchDepth: String = "1") = listOf(
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
    description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
    default = "{}",
)

val CHECK_COMMAND_INPUT = "check-command" to stringInput(
    description = "Command to run for checking",
    required = true,
)

val APP_ID_SECRET = "app-id" to secretInput("GitHub App ID for generating commit token")
val APP_PRIVATE_KEY_SECRET = "app-private-key" to secretInput("GitHub App private key for generating commit token")

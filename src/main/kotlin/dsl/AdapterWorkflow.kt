package dsl

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

/**
 * Generates a YAML file for an adapter workflow that delegates to reusable workflows.
 * Produces YAML directly without github-workflows-kt's workflow() function,
 * eliminating the need for cleanReusableWorkflowJobs() post-processing.
 */
fun generateAdapterWorkflow(
    name: String,
    sourceFileSlug: String,
    targetFileName: String,
    trigger: WorkflowCall,
    jobs: List<ReusableWorkflowJobDef>,
    outputDir: File,
) {
    val yaml = buildString {
        appendHeader(sourceFileSlug)
        appendLine()
        appendLine("name: ${yamlString(name)}")
        appendLine("on:")
        appendWorkflowCallTrigger(trigger)
        appendLine("jobs:")
        jobs.forEach { job ->
            appendJob(job)
        }
    }

    outputDir.mkdirs()
    File(outputDir, targetFileName).writeText(yaml)
}

private fun StringBuilder.appendHeader(slug: String) {
    appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).")
    appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
    appendLine("# Generated with https://github.com/typesafegithub/github-workflows-kt")
}

private fun StringBuilder.appendWorkflowCallTrigger(trigger: WorkflowCall) {
    appendLine("  workflow_call:")

    val inputs = resolveInputs(trigger)
    val secrets = resolveSecrets(trigger)

    if (inputs.isNotEmpty()) {
        appendLine("    inputs:")
        for ((inputName, props) in inputs) {
            appendLine("      $inputName:")
            appendLine("        description: ${yamlString(props.description)}")
            appendLine("        type: ${yamlString(props.type)}")
            appendLine("        required: ${props.required}")
            if (props.default != null) {
                appendLine("        default: ${yamlValue(props.default)}")
            }
        }
    }

    if (secrets.isNotEmpty()) {
        appendLine("    secrets:")
        for ((secretName, secret) in secrets) {
            appendLine("      $secretName:")
            appendLine("        description: ${yamlString(secret.description)}")
            appendLine("        required: ${secret.required}")
        }
    }
}

private data class InputProps(
    val description: String,
    val type: String,
    val required: Boolean,
    val default: Any?,
)

private fun resolveInputs(trigger: WorkflowCall): Map<String, InputProps> {
    // Check for _customArguments-based inputs (used for boolean defaults)
    val customArgs = trigger._customArguments
    if (customArgs.containsKey("inputs")) {
        @Suppress("UNCHECKED_CAST")
        val rawInputs = customArgs["inputs"] as Map<String, Map<String, Any?>>
        return rawInputs.map { (name, props) ->
            name to InputProps(
                description = props["description"] as String,
                type = props["type"] as String,
                required = props["required"] as Boolean,
                default = props["default"],
            )
        }.toMap()
    }

    // Standard inputs
    return trigger.inputs.map { (name, input) ->
        name to InputProps(
            description = input.description,
            type = input.type.name.lowercase(),
            required = input.required,
            default = input.default,
        )
    }.toMap()
}

private fun resolveSecrets(trigger: WorkflowCall): Map<String, WorkflowCall.Secret> {
    return trigger.secrets ?: emptyMap()
}

private fun StringBuilder.appendJob(job: ReusableWorkflowJobDef) {
    appendLine("  ${job.id}:")

    if (job.needs.isNotEmpty()) {
        if (job.needs.size == 1) {
            appendLine("    needs: ${yamlString(job.needs.first())}")
        } else {
            appendLine("    needs:")
            for (need in job.needs) {
                appendLine("    - ${yamlString(need)}")
            }
        }
    }

    if (job.condition != null) {
        appendLine("    if: ${yamlString(job.condition)}")
    }

    if (job.strategy != null) {
        appendStrategy(job.strategy, indent = 4)
    }

    appendLine("    uses: ${yamlString(job.uses.usesString)}")

    if (job.with.isNotEmpty()) {
        appendLine("    with:")
        for ((key, value) in job.with) {
            appendLine("      $key: ${yamlString(value)}")
        }
    }

    if (job.secrets.isNotEmpty()) {
        appendLine("    secrets:")
        for ((key, value) in job.secrets) {
            appendLine("      $key: ${yamlString(value)}")
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun StringBuilder.appendStrategy(strategy: Map<String, Any>, indent: Int) {
    val prefix = " ".repeat(indent)
    appendLine("${prefix}strategy:")
    val matrix = strategy["matrix"] as? Map<String, Any>
    if (matrix != null) {
        appendLine("${prefix}  matrix:")
        for ((key, value) in matrix) {
            appendLine("${prefix}    $key: ${yamlString(value.toString())}")
        }
    }
}

/**
 * Formats a value for YAML output.
 * - Strings are single-quoted with internal single quotes escaped (doubled).
 * - Booleans and numbers are unquoted.
 */
private fun yamlValue(value: Any?): String = when (value) {
    is Boolean -> value.toString()
    is Number -> value.toString()
    is String -> yamlString(value)
    null -> "''"
    else -> yamlString(value.toString())
}

/**
 * Single-quotes a string for YAML, escaping internal single quotes by doubling them.
 */
private fun yamlString(value: String): String {
    val escaped = value.replace("'", "''")
    return "'$escaped'"
}

package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

private val QUOTED_MAP_KEY = Regex("""^(\s*)'([^']*?)'\s*:""", RegexOption.MULTILINE)

private fun unquoteYamlMapKeys(yaml: String): String =
    yaml.replace(QUOTED_MAP_KEY, "$1$2:")

class BuiltAdapterWorkflow(
    val fileName: String,
    val workflowName: String,
    private val inputsYaml: Map<String, InputYaml>?,
    private val jobs: List<ReusableWorkflowJobDef>,
) {
    fun generate(outputDir: File) {
        val collectedSecrets = collectSecretsFromJobs(jobs)

        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = inputsYaml,
                    secrets = collectedSecrets,
                )
            ),
            jobs = jobs.associate { job -> job.id to job.toJobYaml() },
        )

        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (src/main/kotlin/).")
            appendLine("# If you want to modify the workflow, please change the Kotlin source and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }

        val rawBody = adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto)
        val body = unquoteYamlMapKeys(rawBody)

        File(outputDir, fileName).writeText("$header\n\n$body\n")
    }

    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? =
        buildMap {
            for (job in jobDefs) {
                val descriptions = job.uses.secrets.mapValues { it.value.description }
                for (name in job.secrets.keys) {
                    putIfAbsent(name, SecretYaml(description = descriptions[name] ?: name, required = true))
                }
            }
        }.takeIf { it.isNotEmpty() }
}

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputs = linkedMapOf<String, WorkflowCall.Input>()
    private val booleanDefaults = mutableMapOf<String, Boolean>()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(
        name: String,
        description: String,
        default: Boolean? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, false, WorkflowCall.Type.Boolean, null)
        default?.let { booleanDefaults[name] = it }
        return WorkflowInput(name)
    }

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): BuiltAdapterWorkflow = BuiltAdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputs, booleanDefaults),
        jobs = jobs.toList(),
    )
}

fun adapterWorkflow(
    fileName: String,
    name: String,
    block: AdapterWorkflowBuilder.() -> Unit,
): BuiltAdapterWorkflow {
    val builder = AdapterWorkflowBuilder(fileName, name)
    builder.block()
    return builder.build()
}

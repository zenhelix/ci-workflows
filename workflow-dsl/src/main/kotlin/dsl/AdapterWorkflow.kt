package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

private val QUOTED_MAP_KEY = Regex("""^(\s*)'([^']*?)'\s*:""", RegexOption.MULTILINE)

private fun unquoteYamlMapKeys(yaml: String): String =
    yaml.replace(QUOTED_MAP_KEY, "$1$2:")

abstract class AdapterWorkflow(fileName: String) : ReusableWorkflow(fileName) {

    abstract val workflowName: String

    abstract fun jobs(): List<ReusableWorkflowJobDef>

    fun generate(outputDir: File) {
        val jobDefs = jobs()
        val collectedSecrets = collectSecretsFromJobs(jobDefs)

        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = toInputsYaml(),
                    secrets = collectedSecrets,
                )
            ),
            jobs = jobDefs.associate { job -> job.id to job.toJobYaml() },
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

    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? {
        val result = linkedMapOf<String, SecretYaml>()
        for (job in jobDefs) {
            val descriptions = job.uses.secrets.mapValues { (_, secret) -> secret.description }
            for (name in job.secrets.keys) {
                result.putIfAbsent(name, SecretYaml(description = descriptions[name] ?: name, required = true))
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }
}

package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

// Kaml SingleQuoted style quotes map keys too; strip quotes to match GitHub Actions convention.
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

        val slug = fileName.removeSuffix(".yml")
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).")
            appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }

        val rawBody = adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto)
        val body = unquoteYamlMapKeys(rawBody)

        outputDir.mkdirs()
        File(outputDir, fileName).writeText("$header\n\n$body\n")
    }

    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? {
        val secretNames = jobDefs.flatMap { it.secrets.keys }.toSet()
        if (secretNames.isEmpty()) return null

        return secretNames.associateWith { name ->
            val workflowSecret = jobDefs
                .mapNotNull { job -> job.uses.secrets[name] }
                .firstOrNull()

            SecretYaml(
                description = workflowSecret?.description ?: name,
                required = workflowSecret?.required ?: true,
            )
        }
    }
}

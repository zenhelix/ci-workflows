package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

// ---- New AdapterWorkflow base class ----------------------------------------

abstract class AdapterWorkflow(fileName: String) : ReusableWorkflow(fileName) {

    abstract val workflowName: String

    abstract fun jobs(): List<ReusableWorkflowJobDef>

    fun generate(outputDir: File) {
        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = toInputsYaml(),
                    secrets = toSecretsYaml(),
                )
            ),
            jobs = jobs().associate { job -> job.id to job.toJobYaml() },
        )

        val slug = fileName.removeSuffix(".yml")
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).")
            appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }

        val body = adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto)

        outputDir.mkdirs()
        File(outputDir, fileName).writeText("$header\n$body")
    }
}


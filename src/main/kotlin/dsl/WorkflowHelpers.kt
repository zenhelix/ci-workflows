package dsl

import actions.SetupAction
import config.SetupTool
import io.github.typesafegithub.workflows.dsl.JobBuilder
import java.io.File

/** Reference an input of the current workflow: generates "\${{ inputs.<name> }}" */
fun inputRef(name: String) = "\${{ inputs.$name }}"

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    listOf(SetupTool.Gradle, SetupTool.Go, SetupTool.Python).forEach { tool ->
        uses(
            name = "Setup ${tool.id.replaceFirstChar { c -> c.uppercase() }}",
            action = SetupAction(
                tool.actionName, tool.versionKey,
                "\${{ fromJson(inputs.setup-params).${tool.versionKey} || '${tool.defaultVersion}' }}",
                fetchDepth,
            ),
            condition = "inputs.setup-action == '${tool.id}'",
        )
    }
}

fun JobBuilder<*>.noop() {
    run(name = "noop", command = "true")
}

fun cleanReusableWorkflowJobs(targetFile: File) {
    val lines = targetFile.readLines()
    val output = mutableListOf<String>()

    val jobsLineIdx = lines.indexOfFirst { it.trimStart() == "jobs:" }

    var currentJobLines = mutableListOf<String>()
    var currentJobHasUses = false
    var inJobsSection = false
    var i = 0

    fun flushJob() {
        if (!currentJobHasUses) {
            output.addAll(currentJobLines)
        } else {
            var j = 0
            while (j < currentJobLines.size) {
                val buffered = currentJobLines[j]
                val trimmed = buffered.trimStart()
                val bIndent = if (buffered.isBlank()) -1 else buffered.length - trimmed.length

                if (bIndent == 4 && trimmed.startsWith("runs-on:")) {
                    j++
                    continue
                }

                if (bIndent == 4 && trimmed.startsWith("steps:")) {
                    j++
                    while (j < currentJobLines.size) {
                        val stepLine = currentJobLines[j]
                        if (stepLine.isBlank()) {
                            j++; continue
                        }
                        val stepTrimmed = stepLine.trimStart()
                        val sIndent = stepLine.length - stepTrimmed.length
                        if (sIndent < 4) break
                        if (sIndent == 4 && !stepTrimmed.startsWith("-")) break
                        j++
                    }
                    continue
                }

                output.add(buffered)
                j++
            }
        }
        currentJobLines = mutableListOf()
        currentJobHasUses = false
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        val indent = if (line.isBlank()) -1 else line.length - trimmed.length

        if (!inJobsSection) {
            output.add(line)
            if (i == jobsLineIdx) inJobsSection = true
            i++
            continue
        }

        if (indent == 2 && line.trimEnd().endsWith(":")) {
            flushJob()
            currentJobLines.add(line)
            i++
            continue
        }

        if (indent == 0 && !line.isBlank()) {
            flushJob()
            output.add(line)
            inJobsSection = false
            i++
            continue
        }

        if (indent == 4 && trimmed.startsWith("uses:")) {
            currentJobHasUses = true
        }

        currentJobLines.add(line)
        i++
    }

    flushJob()

    targetFile.writeText(output.joinToString("\n") + "\n")
}

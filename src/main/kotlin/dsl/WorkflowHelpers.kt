package dsl

import actions.SetupAction
import io.github.typesafegithub.workflows.dsl.JobBuilder
import java.io.File

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    uses(
        name = "Setup Gradle",
        action = SetupAction(
            "setup-gradle", "java-version",
            "\${{ fromJson(inputs.setup-params).java-version || '17' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupAction(
            "setup-go", "go-version",
            "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupAction(
            "setup-python", "python-version",
            "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'python'",
    )
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

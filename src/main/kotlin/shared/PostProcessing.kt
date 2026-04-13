package shared

import java.io.File

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
                val bIndent = if (buffered.isBlank()) -1 else buffered.length - buffered.trimStart().length

                if (bIndent == 4 && buffered.trimStart().startsWith("runs-on:")) {
                    j++
                    continue
                }

                if (bIndent == 4 && buffered.trimStart().startsWith("steps:")) {
                    j++
                    while (j < currentJobLines.size) {
                        val stepLine = currentJobLines[j]
                        if (stepLine.isBlank()) { j++; continue }
                        val sIndent = stepLine.length - stepLine.trimStart().length
                        if (sIndent < 4) break
                        if (sIndent == 4 && !stepLine.trimStart().startsWith("-")) break
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
        val indent = if (line.isBlank()) -1 else line.length - line.trimStart().length

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

        if (indent == 4 && line.trimStart().startsWith("uses:")) {
            currentJobHasUses = true
        }

        currentJobLines.add(line)
        i++
    }

    flushJob()

    targetFile.writeText(output.joinToString("\n") + "\n")
}

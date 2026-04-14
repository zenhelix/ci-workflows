package generate

import dsl.builder.GeneratableWorkflow
import workflows.base.CheckWorkflow
import workflows.base.ConventionalCommitCheckWorkflow
import workflows.base.CreateTagWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.base.ReleaseWorkflow
import workflows.base.PublishWorkflow
import workflows.base.LabelerWorkflow
import workflows.base.AppDeployWorkflow
import workflows.adapters.check.GradleCheck
import workflows.adapters.tag.CreateTagAdapters
import workflows.adapters.tag.ManualCreateTagAdapters
import workflows.adapters.release.ReleaseAdapters
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    listOf<GeneratableWorkflow>(
        // Base workflows
        CheckWorkflow,
        ConventionalCommitCheckWorkflow,
        CreateTagWorkflow,
        ManualCreateTagWorkflow,
        ReleaseWorkflow,
        PublishWorkflow,
        LabelerWorkflow,
        AppDeployWorkflow,

        // Adapters — check
        GradleCheck.appCheck,
        GradleCheck.gradleCheck,
        GradleCheck.gradlePluginCheck,
        GradleCheck.kotlinLibraryCheck,

        // Adapters — tag
        CreateTagAdapters.gradle,
        CreateTagAdapters.go,
        ManualCreateTagAdapters.gradle,
        ManualCreateTagAdapters.go,

        // Adapters — release
        ReleaseAdapters.app,
        ReleaseAdapters.gradlePlugin,
        ReleaseAdapters.kotlinLibrary,
    ).forEach { it.generate(outputDir) }
}

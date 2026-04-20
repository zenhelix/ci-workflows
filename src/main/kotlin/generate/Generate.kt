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
import workflows.base.ShaPinningGuardWorkflow
import workflows.base.CodeqlAnalysisWorkflow
import workflows.base.VerifyWorkflowsWorkflow
import workflows.base.DependabotRegenHintWorkflow
import workflows.adapters.check.GradleCheck
import workflows.adapters.tag.TagAdapters
import workflows.adapters.release.ReleaseAdapters
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    val baseWorkflows = listOf<GeneratableWorkflow>(
        CheckWorkflow,
        ConventionalCommitCheckWorkflow,
        CreateTagWorkflow,
        ManualCreateTagWorkflow,
        ReleaseWorkflow,
        PublishWorkflow,
        LabelerWorkflow,
        AppDeployWorkflow,
        ShaPinningGuardWorkflow,
        CodeqlAnalysisWorkflow,
        VerifyWorkflowsWorkflow,
        DependabotRegenHintWorkflow,
    )

    val adapterWorkflows = GradleCheck.all + TagAdapters.all + ReleaseAdapters.all

    (baseWorkflows + adapterWorkflows).forEach { it.generate(outputDir) }
}

package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.inputRefProp
import workflows.ProjectWorkflow

object LabelerWorkflow : ProjectWorkflow("labeler.yml") {

    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by inputRefProp(LabelerWorkflow.configPath)
    }
}

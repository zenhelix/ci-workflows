package workflows

import config.reusableWorkflow
import dsl.ReusableWorkflow

abstract class ProjectWorkflow(fileName: String) : ReusableWorkflow(fileName) {
    override val usesString: String = reusableWorkflow(fileName)
}

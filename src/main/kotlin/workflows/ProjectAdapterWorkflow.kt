package workflows

import config.reusableWorkflow
import dsl.AdapterWorkflow

abstract class ProjectAdapterWorkflow(fileName: String) : AdapterWorkflow(fileName) {
    override val usesString: String = reusableWorkflow(fileName)
}

package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateGoManualCreateTag(outputDir: File) {
    val targetFile = "go-manual-create-tag.yml"

    workflow(
        name = "Go Manual Create Tag",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/go-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "tag-version" to stringInput(
                            description = "Version to tag (e.g. 1.2.3)",
                            required = true,
                        ),
                        "go-version" to stringInput(
                            description = "Go version to use",
                            default = DEFAULT_GO_VERSION,
                        ),
                        "check-command" to stringInput(
                            description = "Go validation command",
                            default = "make test",
                        ),
                        "tag-prefix" to stringInput(
                            description = "Prefix for the tag",
                            default = "v",
                        ),
                    ),
                    "secrets" to mapOf(
                        APP_ID_SECRET,
                        APP_PRIVATE_KEY_SECRET,
                    ),
                ),
            ),
        ),
    ) {
        job(
            id = "manual-tag",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "uses" to reusableWorkflow("manual-create-tag.yml"),
                "with" to mapOf(
                    "tag-version" to "\${{ inputs.tag-version }}",
                    "tag-prefix" to "\${{ inputs.tag-prefix }}",
                    "setup-action" to "go",
                    "setup-params" to "{\"go-version\": \"\${{ inputs.go-version }}\"}",
                    "check-command" to "\${{ inputs.check-command }}",
                ),
                "secrets" to mapOf(
                    "app-id" to "\${{ secrets.app-id }}",
                    "app-private-key" to "\${{ secrets.app-private-key }}",
                ),
            ),
        ) {
            noop()
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

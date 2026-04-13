package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateManualCreateTag(outputDir: File) {
    workflow(
        name = "Manual Create Tag",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/manual-create-tag.main.kts"),
        targetFileName = "manual-create-tag.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "tag-version" to stringInput(
                            description = "Version to tag (e.g. 1.2.3)",
                            required = true,
                        ),
                        "tag-prefix" to stringInput(
                            description = "Prefix for the tag (e.g. v)",
                            default = "",
                        ),
                        SETUP_ACTION_INPUT.let { (k, v) ->
                            k to stringInput(
                                description = "Setup action to use: gradle, go, python",
                                required = true,
                            )
                        },
                        SETUP_PARAMS_INPUT,
                        "check-command" to stringInput(
                            description = "Validation command to run before tagging",
                            required = true,
                        ),
                    ),
                    "secrets" to mapOf(
                        APP_ID_SECRET,
                        APP_PRIVATE_KEY_SECRET,
                    ),
                ),
            ),
            "permissions" to mapOf("contents" to "write"),
        ),
    ) {
        job(
            id = "manual_tag",
            name = "Manual Tag",
            runsOn = UbuntuLatest,
        ) {
            run(
                name = "Validate version format",
                command = """
                    VERSION="${'$'}{{ inputs.tag-version }}"
                    if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
                      echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
                      exit 1
                    fi
                """.trimIndent(),
            )
            conditionalSetupSteps(fetchDepth = "0")
            run(
                name = "Run validation",
                command = "\${{ inputs.check-command }}",
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = "\${{ secrets.app-id }}",
                    appPrivateKey = "\${{ secrets.app-private-key }}",
                ),
                id = "app-token",
            )
            run(
                name = "Create and push tag",
                command = """
                    TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
                    git config user.name "github-actions[bot]"
                    git config user.email "github-actions[bot]@users.noreply.github.com"
                    git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
                    git push origin "${'$'}TAG"
                    echo "::notice::Created tag ${'$'}TAG"
                """.trimIndent(),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}",
                ),
            )
        }
    }
}

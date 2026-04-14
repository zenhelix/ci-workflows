package workflows.base

import actions.CreateAppTokenAction
import workflows.ManualCreateTagWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateManualCreateTag() {
    workflow(
        name = "Manual Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = ManualCreateTagWorkflow.inputs,
                secrets = ManualCreateTagWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/manual-create-tag.main.kts"),
        targetFileName = "manual-create-tag.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
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
                command = ManualCreateTagWorkflow.checkCommand.ref.expression,
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = ManualCreateTagWorkflow.appId.ref.expression,
                    appPrivateKey = ManualCreateTagWorkflow.appPrivateKey.ref.expression,
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

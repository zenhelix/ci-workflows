package workflows.base

import actions.CreateAppTokenAction
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import dsl.core.expr
import workflows.support.conditionalSetupSteps

object ManualCreateTagWorkflow : ProjectWorkflow(
    "manual-create-tag.yml", "Manual Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    val tagVersion = input("tag-version", "Version to tag (e.g. 1.2.3)", required = true)
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<ManualCreateTagWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@ManualCreateTagWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "manual_tag", name = "Manual Tag", runsOn = UbuntuLatest) {
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
            run(name = "Run validation", command = checkCommand.expr)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(appId = appId.expr, appPrivateKey = appPrivateKey.expr),
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
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}"),
            )
        }
    }
}

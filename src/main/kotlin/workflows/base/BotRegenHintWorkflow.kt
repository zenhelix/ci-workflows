package workflows.base

import dsl.builder.GeneratableWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.actions.actions.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle_Untyped
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object BotRegenHintWorkflow : GeneratableWorkflow {
    override val fileName: String = "bot-regen-hint.yml"

    private val TRIGGER_PATHS = listOf("build.gradle.kts", "src/**", "workflow-dsl/**")

    // Auto-commit script. Runs after `./gradlew run` regeneration.
    // - Fast-exits if no diff (generator output matches committed YAML).
    // - Bails with a comment if diff touches files outside .github/workflows/.
    // - Determinism guard: second `./gradlew run`; bail if it produces another diff.
    // - Otherwise commits as github-actions[bot] and pushes with --force-with-lease.
    private val AUTO_COMMIT_SCRIPT = $$"""
        set -euo pipefail

        if git diff --quiet; then
          echo "No regen required — generator output matches committed YAML."
          exit 0
        fi

        if ! git diff --quiet -- ':!.github/workflows/'; then
          echo "::warning::Bot PR touches files outside .github/workflows/ — manual review required."
          gh pr comment "$PR_NUMBER" \
            --body "Bot PR touches files outside .github/workflows/. Auto-regen aborted; please review and run \`./gradlew run\` locally."
          exit 1
        fi

        DIFF_FIRST=$(git diff)
        ./gradlew run
        DIFF_SECOND=$(git diff)
        if [ "$DIFF_FIRST" != "$DIFF_SECOND" ]; then
          echo "::warning::Generator output non-deterministic across two runs — bailing to avoid commit loop."
          gh pr comment "$PR_NUMBER" \
            --body "Generator output is non-deterministic on this PR (second \`./gradlew run\` produced a different diff). Auto-regen aborted; please investigate locally."
          exit 1
        fi

        git config user.name "github-actions[bot]"
        git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
        git add .github/workflows/
        git commit -m "chore: regenerate workflows after bot bump"
        git push --force-with-lease origin "HEAD:$PR_BRANCH"
    """.trimIndent()

    override fun generate(outputDir: File) {
        workflow(
            name = "Bot Regen Hint",
            on = listOf(PullRequest(paths = TRIGGER_PATHS)),
            sourceFile = File("src/main/kotlin/workflows/base/BotRegenHintWorkflow.kt"),
            targetFileName = fileName,
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(
                Permission.Contents to Mode.Write,
                Permission.PullRequests to Mode.Write,
            ),
            concurrency = Concurrency(
                group = $$"${{ github.workflow }}-${{ github.event.pull_request.number }}",
                cancelInProgress = true,
            ),
        ) {
            job(
                id = "regen-and-commit",
                name = "Regenerate workflows and commit if drift",
                runsOn = UbuntuLatest,
                condition = $$"${{ github.actor == 'renovate[bot]' || github.actor == 'dependabot[bot]' }}",
            ) {
                uses(
                    name = "Check out PR head",
                    action = Checkout_Untyped(
                        ref_Untyped = $$"${{ github.event.pull_request.head.ref }}",
                        fetchDepth_Untyped = "0",
                        token_Untyped = $$"${{ secrets.WORKFLOW_BOT_PAT }}",
                    ),
                )
                uses(
                    name = "Set up JDK",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "temurin",
                        javaVersion_Untyped = "17",
                    ),
                )
                uses(name = "Setup Gradle", action = ActionsSetupGradle_Untyped())
                run(name = "Regenerate workflows", command = "./gradlew run")
                run(
                    name = "Auto-commit regen if drift",
                    command = AUTO_COMMIT_SCRIPT,
                    env = linkedMapOf(
                        "GH_TOKEN" to $$"${{ secrets.WORKFLOW_BOT_PAT }}",
                        "PR_BRANCH" to $$"${{ github.event.pull_request.head.ref }}",
                        "PR_NUMBER" to $$"${{ github.event.pull_request.number }}",
                    ),
                )
            }
        }
    }
}

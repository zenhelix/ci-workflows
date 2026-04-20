package workflows.base

import dsl.builder.GeneratableWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.actions.actions.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle_Untyped
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object DependabotRegenHintWorkflow : GeneratableWorkflow {
    override val fileName: String = "dependabot-regen-hint.yml"

    private val TRIGGER_PATHS = listOf("build.gradle.kts", "src/**", "workflow-dsl/**")

    // Shell script posted as PR comment if regen drift is detected.
    // Uses gh CLI with GITHUB_TOKEN for the `pull-requests: write` scope.
    // Truncates diff to ~3000 chars so the comment body stays readable.
    // NOTE: `$$"..."` raw strings (Kotlin 2.x) treat `$` as literal; use `${'$'}` for
    // interpolation. GitHub expressions `${{ ... }}` and shell vars `$VAR` render verbatim.
    private val POST_COMMENT_SCRIPT = $$"""
        set -euo pipefail
        if git diff --exit-code .github/workflows/; then
          echo "No regen required — generator output matches committed YAML."
          exit 0
        fi
        DIFF=$(git diff .github/workflows/ | head -c 3000)
        PR_BRANCH="${{ github.event.pull_request.head.ref }}"
        PR_NUMBER="${{ github.event.pull_request.number }}"
        BODY=$(cat <<COMMENT
        **Regen required.** The generator output differs from the committed YAMLs.

        Please run locally and push:

        \`\`\`
        git fetch && git checkout $PR_BRANCH
        ./gradlew run
        git add .github/workflows/
        git commit --amend --no-edit
        git push --force-with-lease
        \`\`\`

        Preview of the drift (truncated to 3000 chars):

        \`\`\`diff
        $DIFF
        \`\`\`
        COMMENT
        )
        gh pr comment "$PR_NUMBER" --body "$BODY"
    """.trimIndent()

    override fun generate(outputDir: File) {
        workflow(
            name = "Dependabot Regen Hint",
            on = listOf(PullRequest(paths = TRIGGER_PATHS)),
            sourceFile = File("src/main/kotlin/workflows/base/DependabotRegenHintWorkflow.kt"),
            targetFileName = fileName,
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(
                Permission.Contents to Mode.Read,
                Permission.PullRequests to Mode.Write,
            ),
        ) {
            job(
                id = "regen-hint",
                name = "Post regen hint if generator output drifts",
                runsOn = UbuntuLatest,
                condition = $$"${{ github.actor == 'dependabot[bot]' }}",
            ) {
                uses(
                    name = "Check out PR head",
                    action = Checkout_Untyped(
                        ref_Untyped = $$"${{ github.event.pull_request.head.sha }}",
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
                    name = "Post comment if regen required",
                    command = POST_COMMENT_SCRIPT,
                    env = linkedMapOf("GH_TOKEN" to $$"${{ secrets.GITHUB_TOKEN }}"),
                )
            }
        }
    }
}

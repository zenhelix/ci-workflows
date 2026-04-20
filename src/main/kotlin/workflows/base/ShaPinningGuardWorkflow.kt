package workflows.base

import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object ShaPinningGuardWorkflow : ProjectWorkflow(
    "sha-pinning-guard.yml",
    "SHA Pinning Guard",
) {
    override fun WorkflowBuilder.implementation() {
        job(id = "sha-pinning-guard", name = "SHA Pinning Guard", runsOn = UbuntuLatest, timeoutMinutes = 5) {
            uses(
                name = "Checkout caller repo",
                action = Checkout_Untyped(),
            )
            uses(
                name = "Checkout ci-workflows script (sparse)",
                action = Checkout_Untyped(
                    repository_Untyped = "zenhelix/ci-workflows",
                    ref_Untyped = $$"${{ github.job_workflow_sha }}",
                    path_Untyped = ".ci-workflows-src",
                    sparseCheckout_Untyped = ".github/scripts",
                ),
            )
            run(
                name = "Run SHA pinning guardrail",
                command = "python3 .ci-workflows-src/.github/scripts/check-sha-pinning.py",
            )
        }
    }
}

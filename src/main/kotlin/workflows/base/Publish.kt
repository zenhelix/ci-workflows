package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import dsl.PublishWorkflow
import dsl.conditionalSetupSteps
import java.io.File

fun generatePublish(outputDir: File) {
    workflow(
        name = "Publish",
        on = listOf(
            WorkflowCall(
                inputs = PublishWorkflow.inputs,
                secrets = PublishWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/publish.main.kts"),
        targetFileName = "publish.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Read),
    ) {
        job(
            id = "publish",
            name = "Publish",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = "\${{ inputs.publish-command }}",
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                    "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
                    "ORG_GRADLE_PROJECT_signingKeyId" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                    "ORG_GRADLE_PROJECT_signingPublicKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingPassword" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
                    "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                    "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                ),
            )
        }
    }
}

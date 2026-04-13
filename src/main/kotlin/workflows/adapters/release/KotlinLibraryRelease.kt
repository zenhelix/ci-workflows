package workflows.adapters.release

import config.CommonInputs
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.cleanReusableWorkflowJobs
import dsl.inputRef
import dsl.reusableWorkflowJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateKotlinLibraryRelease(outputDir: File) {
    val targetFile = "kotlin-library-release.yml"

    workflow(
        name = "Kotlin Library Release",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    CommonInputs.javaVersion(),
                    CommonInputs.publishCommand("Gradle publish command for Maven Central"),
                    CommonInputs.changelogConfig(),
                ),
                secrets = MAVEN_SONATYPE_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/kotlin-library-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
        }

        reusableWorkflowJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction(SetupTool.Gradle.id)
            PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
            PublishWorkflow.publishCommand(inputRef("publish-command"))
            secrets(MAVEN_SONATYPE_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

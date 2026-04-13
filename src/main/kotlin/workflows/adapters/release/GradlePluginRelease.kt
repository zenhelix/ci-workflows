package workflows.adapters.release

import config.CommonInputs
import config.GRADLE_PORTAL_SECRETS
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

fun generateGradlePluginRelease(outputDir: File) {
    val targetFile = "gradle-plugin-release.yml"

    workflow(
        name = "Gradle Plugin Release",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    CommonInputs.javaVersion(),
                    CommonInputs.publishCommand("Gradle publish command (publishes to both Maven Central and Gradle Portal)"),
                    CommonInputs.changelogConfig(),
                ),
                secrets = MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-plugin-release.main.kts"),
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
            secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}

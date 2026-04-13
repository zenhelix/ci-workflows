package workflows.adapters.release

import config.CommonInputs
import config.GRADLE_PORTAL_SECRETS
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGradlePluginRelease(outputDir: File) {
    generateAdapterWorkflow(
        name = "Gradle Plugin Release",
        sourceFileSlug = "gradle-plugin-release",
        targetFileName = "gradle-plugin-release.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.publishCommand("Gradle publish command (publishes to both Maven Central and Gradle Portal)"),
                CommonInputs.changelogConfig(),
            ),
            secrets = MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS,
        ),
        jobs = listOf(
            reusableJob(id = "release", uses = ReleaseWorkflow) {
                ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
            },
            reusableJob(id = "publish", uses = PublishWorkflow) {
                needs("release")
                PublishWorkflow.setupAction(SetupTool.Gradle.id)
                PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
                PublishWorkflow.publishCommand(inputRef("publish-command"))
                secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

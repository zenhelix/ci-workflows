package workflows.adapters.release

import config.CommonInputs
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

fun generateKotlinLibraryRelease(outputDir: File) {
    generateAdapterWorkflow(
        name = "Kotlin Library Release",
        sourceFileSlug = "kotlin-library-release",
        targetFileName = "kotlin-library-release.yml",
        trigger = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.publishCommand("Gradle publish command for Maven Central"),
                CommonInputs.changelogConfig(),
            ),
            secrets = MAVEN_SONATYPE_SECRETS,
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
                secrets(MAVEN_SONATYPE_SECRETS.passthrough())
            },
        ),
        outputDir = outputDir,
    )
}

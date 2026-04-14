package generate

import config.GO_TAG
import config.GRADLE_TAG
import workflows.CheckWorkflow
import workflows.ConventionalCommitCheckWorkflow
import workflows.CreateTagWorkflow
import workflows.ManualCreateTagWorkflow
import workflows.ReleaseWorkflow
import workflows.PublishWorkflow
import workflows.LabelerWorkflow
import workflows.AppDeployWorkflow
import workflows.adapters.check.gradleCheck
import workflows.adapters.release.appRelease
import workflows.adapters.release.gradleReleaseWorkflow
import workflows.adapters.tag.toolCreateTag
import workflows.adapters.tag.toolManualCreateTag
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    // Base workflows
    CheckWorkflow.generate()
    ConventionalCommitCheckWorkflow.generate()
    CreateTagWorkflow.generate()
    ManualCreateTagWorkflow.generate()
    ReleaseWorkflow.generate()
    PublishWorkflow.generate()
    LabelerWorkflow.generate()
    AppDeployWorkflow.generate()

    // Adapter workflows
    listOf(
        // Check adapters
        gradleCheck("app-check.yml", "Application Check"),
        gradleCheck("gradle-check.yml", "Gradle Check"),
        gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check"),
        gradleCheck("kotlin-library-check.yml", "Kotlin Library Check"),

        // Tag adapters
        toolCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE_TAG),
        toolCreateTag("go-create-tag.yml", "Go Create Tag", GO_TAG),
        toolManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE_TAG),
        toolManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO_TAG),

        // Release adapters
        appRelease,
        gradleReleaseWorkflow(
            fileName = "gradle-plugin-release.yml",
            name = "Gradle Plugin Release",
            publishDescription = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
        ),
        gradleReleaseWorkflow(
            fileName = "kotlin-library-release.yml",
            name = "Kotlin Library Release",
            publishDescription = "Gradle publish command for Maven Central",
            publishSecrets = {
                passthroughSecrets(
                    PublishWorkflow.mavenSonatypeUsername,
                    PublishWorkflow.mavenSonatypeToken,
                    PublishWorkflow.mavenSonatypeSigningKeyId,
                    PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                    PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                    PublishWorkflow.mavenSonatypeSigningPassword,
                )
            },
        ),
    ).forEach { it.generate(outputDir) }
}

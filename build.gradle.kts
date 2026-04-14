plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("generate.GenerateKt")
}

repositories {
    mavenCentral()
    maven("https://bindings.krzeminski.it")
}

dependencies {
    implementation(project(":workflow-dsl"))
    implementation(libs.github.workflows.kt)

    // JIT action bindings
    implementation("actions:checkout:v6")
    // mathieudutour:github-tag-action:v6 - not yet available in bindings.krzeminski.it registry
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}

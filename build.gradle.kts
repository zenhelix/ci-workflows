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

    // JIT action bindings — SHA-pinned. Bump via Dependabot (see .github/dependabot.yml).
    // NOTE: SHA coordinates only expose `*_Untyped` classes; use _Untyped wrappers at call sites.
    implementation("actions:checkout:de0fac2e4500dabe0009e67214ff5f5447ce83dd") // v6
    // mathieudutour:github-tag-action — not in bindings.krzeminski.it registry; see actions/GithubTagAction.kt
    implementation("mikepenz:release-changelog-builder-action:bcae7115752d4ed746ff92feb666574428a79415") // v6
    implementation("softprops:action-gh-release:3bb12739c298aeb8a4eeaf626c5b8d85266b0e65") // v2
    implementation("actions:labeler:634933edcd8ababfe52f92936142cc22ac488b1b") // v6
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

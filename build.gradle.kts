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
    implementation("actions:setup-java:be666c2fcd27ec809703dec50e508c2fdc7f6654") // v5
    // gradle/actions and github/codeql-action are multi-action repos. The root
    // `<owner>:<repo>:<sha>` coord only yields a stub `*_Untyped` binding for
    // the repo root (which has no action). Sub-actions are published under
    // `<owner>:<repo>__<subpath>:<sha>` — one Maven artifact per sub-action.
    implementation("gradle:actions__setup-gradle:0723195856401067f7a2779048b490ace7a47d7c") // v5
    implementation("github:codeql-action__init:v4") // v3
    implementation("github:codeql-action__analyze:v4") // v3
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

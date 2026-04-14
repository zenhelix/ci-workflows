plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.github.workflows.kt)
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
}

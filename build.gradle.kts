plugins {
    kotlin("jvm") version "2.3.20"
    application
}

application {
    mainClass.set("generate.GenerateKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")
}

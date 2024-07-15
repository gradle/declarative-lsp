plugins {
    `java-library`
}

description = "Models specific to the Gradle Declarative LSP server"

dependencies {
    implementation(libs.gradle.tooling.api)
    implementation(libs.gradle.declarative.dsl.tooling.models)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
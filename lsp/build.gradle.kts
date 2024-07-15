import org.gradle.declarative.buildlogic.ClasspathWriter

plugins {
    alias(libs.plugins.jvm)
}

dependencies {
    implementation(project(":model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.lsp4j)
    implementation(libs.gradle.tooling.api)
    implementation(libs.gradle.declarative.dsl.core)
    implementation(libs.gradle.declarative.dsl.evaluator)
    implementation(libs.gradle.declarative.dsl.tooling.models)

    testImplementation(libs.logback.classic)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }
            useKotlinTest("2.0.0")
        }
    }
}

tasks {
    val writeRuntimeClasspath by registering(ClasspathWriter::class) {
        group = "build"
        description = "Write the runtime classpath to a file"
    }

    val jar by getting {
        finalizedBy(writeRuntimeClasspath)
    }
}
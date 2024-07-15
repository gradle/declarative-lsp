import org.gradle.declarative.buildlogic.ClasspathWriter
import java.net.URI

plugins {
    alias(libs.plugins.jvm)
}

repositories {
    mavenCentral()
    maven {
        url = URI.create("https://repo.gradle.org/gradle/libs-releases")
    }
}

dependencies {
    implementation(libs.lsp4j)
    implementation(libs.gradle.tooling.api)
    implementation(libs.gradle.declarative.dsl.core)
    implementation(libs.gradle.declarative.dsl.evaluator)
    implementation(libs.gradle.declarative.dsl.tooling.models)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
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
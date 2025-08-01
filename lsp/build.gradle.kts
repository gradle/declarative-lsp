import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
    `maven-publish`
}

dependencies {
    implementation(project(":tapi-model"))

    api(libs.lsp4j)
    api(libs.gradle.tooling.api)
    api(libs.gradle.declarative.dsl.api)
    api(libs.gradle.declarative.dsl.core)
    api(libs.gradle.declarative.dsl.evaluator)
    api(libs.gradle.declarative.dsl.tooling.models)
    api(libs.logback.classic)
}

detekt {
    config.convention(
        project.isolated.rootProject.projectDirectory.file("gradle/detekt.yml")
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            group = "org.gradle"
            artifactId = "declarative-lsp"
            version = "0.0.1-${timestamp()}-SNAPSHOT"
            artifact(tasks.shadowJar)
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/gradle/declarative-lsp")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

testing {
    suites {
        withType<JvmTestSuite> {
            useKotlinTest()
            dependencies {
                implementation(project())
                implementation(libs.mockk)
                implementation(libs.kotlin.reflect)
                implementation(libs.junit5.parameterized)
            }
        }

        val test by getting(JvmTestSuite::class)

        register("integrationTest", JvmTestSuite::class) {
            sources {
                java {
                    setSrcDirs(listOf("src/integTest"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

tasks {
    shadowJar {
        manifest {
            attributes["Main-Class"] = "org.gradle.declarative.lsp.MainKt"
        }
    }

    check {
        dependsOn(testing.suites)
    }
}

fun timestamp(): String {
    val time = ZonedDateTime.now(ZoneId.of("UTC"))
    return time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
}

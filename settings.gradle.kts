dependencyResolutionManagement {
    repositories {
        maven(url = "https://repo.gradle.org/gradle/libs-snapshots") {
            content {
                includeGroup("org.gradle")
            }
        }
        maven(url = "https://repo.gradle.org/gradle/libs-releases") {
            content {
                includeGroup("org.gradle")
            }
        }
        // Use Maven Central for resolving dependencies.
        mavenCentral()
    }
}

rootProject.name = "declarative-lsp"
include("lsp")

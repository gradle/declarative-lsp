package org.gradle.declarative.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class ClasspathWriter: DefaultTask() {
    @TaskAction
    fun writeClasspath() {
        // Get the classpath of the used dependencies
        val classpath = project
            .configurations
            .getByName("runtimeClasspath")
            .files

        // Add the output directories of the source sets
        classpath.add(
            project.tasks.getByName("jar").outputs.files.singleFile
        )

        project
            .layout
            .buildDirectory
            .file("runtime-classpath.txt")
            .get()
            .asFile
            .writeText(classpath.joinToString(":"))
    }

}
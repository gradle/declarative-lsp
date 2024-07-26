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
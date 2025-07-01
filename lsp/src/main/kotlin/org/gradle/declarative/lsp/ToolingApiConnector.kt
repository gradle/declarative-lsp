/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.declarative.lsp

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Utility object helping the handling of the Gradle Tooling API connections.
 */
object ToolingApiConnector {

    val LOGGER: Logger = LoggerFactory.getLogger(ToolingApiConnector::class.java)

    /**
     * Executes the given action using the Gradle Tooling API for the specified project root.
     *
     * @param projectRoot The root directory of the Gradle project.
     * @param action The action to execute in the scope of the Tooling API connection.
     * @return The result of the action, or `null` if an error occurred.
     */
    fun <T> withToolingApi(
        projectRoot: File,
        action: (ProjectConnection) -> T
    ): T? {
        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            return action(connection)
        } catch (e: Exception) {
            LOGGER.error("Failed to execute Tooling API action", e)
            return null
        } finally {
            connection?.close()
        }
    }

}
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

package org.gradle.declarative.lsp

import org.gradle.declarative.lsp.build.action.GetDeclarativeResourcesModel
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.slf4j.LoggerFactory
import java.io.File

private val LOGGER = LoggerFactory.getLogger(TapiConnectionHandler::class.java)

class TapiConnectionHandler(val projectRoot: File): ProgressListener {

    fun getDeclarativeResources(): DeclarativeResourcesModel {
        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            return connection
                .action(GetDeclarativeResourcesModel())
                .addProgressListener(this)
                .run()
        } finally {
            connection?.close()
        }
    }

    override fun statusChanged(event: ProgressEvent?) {
        LOGGER.info("${event?.description}")
    }
}

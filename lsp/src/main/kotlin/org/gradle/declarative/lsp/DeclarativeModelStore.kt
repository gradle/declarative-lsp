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

import org.gradle.declarative.lsp.build.action.GetDeclarativeResourcesModel
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.slf4j.LoggerFactory
import java.io.File

/**
 * This class is responsible for managing the state of the underlying project's declarative model,
 * fetched by the Tooling API.
 */
class DeclarativeModelStore(val projectRoot: File) {
    /**
     * The state of the last successful model retrieval.
     * Can be null if not yet retrieved or if the synchronization resulted in an error.
     */
    var syncState = SyncState.UNSYNCED
        private set

    private var declarativeModel: DeclarativeResourcesModel? = null

    /**
     * Updates the declarative model by executing a model builder over the TAPI.
     */
    fun updateModel() {
        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            syncState = SyncState.SYNCED
            declarativeModel = connection
                .action(GetDeclarativeResourcesModel())
                .run()
        } catch (ex: Exception) {
            LOGGER.error("Failed to execute Tooling API action", ex)
            syncState = SyncState.FAILED_SYNC
            throw ex
        } finally {
            connection?.close()
        }
    }

    /**
     * Executes the given action if the declarative model is available.
     * If the model is not available, it logs a warning and does nothing.
     *
     * @param action The action to perform with the declarative model.
     */
    fun <T> ifAvailable(action: (DeclarativeResourcesModel) -> T): T? {
        if (declarativeModel != null) {
            return action(declarativeModel!!)
        } else {
            LOGGER.warn("Declarative model is not available.")
            return null
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeModelStore::class.java)
    }
}

enum class SyncState {
    UNSYNCED,
    SYNCED,
    FAILED_SYNC
}

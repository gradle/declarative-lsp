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

import org.gradle.declarative.lsp.ToolingApiConnector.withToolingApi
import org.gradle.declarative.lsp.build.action.GetDeclarativeResourcesModel
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.slf4j.LoggerFactory
import java.io.File

/**
 * This class is responsible for storing, updating, and managing downstream dependencies of [DeclarativeResourcesModel].
 *
 * This complicated state management is necessary because the underlying Gradle model is:
 *  - Not always available (project might be broken)
 *  - Can change at any time (e.g. when a user edits the build file)
 */
class DeclarativeModelStore(val projectRoot: File) {

    /**
     * The state of the last successful model retrieval.
     * Can be null if not yet retrieved or if the synchronization resulted in an error.
     */
    private var declarativeModel: DeclarativeResourcesModel? = null

    /**
     * Updates the declarative model by executing a model builder over the TAPI.
     */
    fun updateModel() {
        declarativeModel = withToolingApi(projectRoot) {
            it.action(GetDeclarativeResourcesModel()).run()
        }
    }

    /**
     * Checks if the declarative model is available.
     * @return `true` if the model is available, `false` otherwise.
     */
    fun isAvailable(): Boolean {
        return declarativeModel != null
    }

    /**
     * Executes the given action if the declarative model is available.
     * If the model is not available, it logs a warning and does nothing.
     *
     * @param action The action to perform with the declarative model.
     */
    fun ifAvailable(action: (DeclarativeResourcesModel) -> Unit) {
        if (!isAvailable()) {
            LOGGER.warn("Declarative model is not available, cannot perform action.")
            return
        } else {
            action(declarativeModel!!)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeModelStore::class.java)
    }
}



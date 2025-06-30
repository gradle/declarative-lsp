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
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.slf4j.LoggerFactory
import java.io.File

/**
 * This class is reponsible for storing, updating, and managing downstream dependencies of [DeclarativeResourcesModel].
 *
 * This complicated state management is necessary because the underlying Gradle model is:
 *  - Not always available (project might be broken)
 *  - Can change at any time (e.g. when a user edits the build file)
 */
class DeclarativeModelStore(val projectRoot: File) {

    private var declarativeModel: DeclarativeResourcesModel? = null

    fun updateModel() {
        declarativeModel = withToolingApi(projectRoot) {
            it.getModel(DeclarativeResourcesModel::class.java)
        }
    }

    fun schemaAnalysisEvaluator(): SimpleAnalysisEvaluator? = declarativeModel?.let {
        SimpleAnalysisEvaluator.withSchema(
            declarativeModel!!.settingsInterpretationSequence,
            declarativeModel!!.projectInterpretationSequence
        )
    }



    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeModelStore::class.java)
    }
}



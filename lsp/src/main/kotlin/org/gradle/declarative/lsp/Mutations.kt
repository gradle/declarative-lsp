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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationRequest
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameter
import org.gradle.internal.declarativedsl.dom.mutation.NewElementNodeProvider
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocation
import org.gradle.internal.declarativedsl.dom.mutation.alsoInNestedScopes
import org.gradle.internal.declarativedsl.dom.mutation.elementFromString

class AddDependency: MutationDefinition {
    override val id = "org.gradle.declarative.lsp.AddDependency"
    override val name = "Add dependency"
    override val description = "Adds a new dependency"
    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = true

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> = listOf(
        ModelMutationRequest(
            ScopeLocation.fromTopLevel().alsoInNestedScopes(),
            ModelMutation.AddNewElement(NewElementNodeProvider.Constant(elementFromString("f()")!!))
        )
    )
}
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

package org.gradle.declarative.lsp.mutation.definition

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.lsp.extension.typeByFqn
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationRequest
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameter
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind
import org.gradle.internal.declarativedsl.dom.mutation.NewElementNodeProvider
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocation
import org.gradle.internal.declarativedsl.dom.mutation.elementFromString
import org.gradle.internal.declarativedsl.dom.mutation.inObjectsConfiguredBy
import org.gradle.internal.declarativedsl.dom.mutation.inObjectsOfType
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

class AddDependency: MutationDefinition {
    override val id: String = "addDependency"
    override val name: String = "Add Dependency"
    override val description: String = "Add a dependency to the project"
    override val parameters: List<MutationParameter<*>> = listOf(
        MutationParameter(
            "scope",
            "The scope of the dependency (e.g. 'implementation', 'api').",
            MutationParameterKind.StringParameter
        ),
        MutationParameter(
            "coordinate",
            "The coordinate of the dependency (e.g. \"g:a:v\"). Also can be more complex, scoped coordinate (e.g. \"project(':subproject')\").",
            MutationParameterKind.StringParameter
        )
    )

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema
            .dataClassTypesByFqName
            .keys
            .any { it.qualifiedName == "org.gradle.api.experimental.common.HasLibraryDependencies" }

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> {
        val scopeForDependenciesBlock = ScopeLocation.fromTopLevel().inObjectsOfType(
            projectAnalysisSchema.typeByFqn("org.gradle.api.experimental.common.HasLibraryDependencies")
        )
        val dependenciesFunction = projectAnalysisSchema
            .typeByFqn("org.gradle.api.experimental.common.HasLibraryDependencies")
            .singleFunctionNamed("dependencies")

        return listOf(
            ModelMutationRequest(
                scopeForDependenciesBlock,
                ModelMutation.AddConfiguringBlockIfAbsent(dependenciesFunction)
            ),
            ModelMutationRequest(
                scopeForDependenciesBlock.inObjectsConfiguredBy(dependenciesFunction),
                ModelMutation.AddNewElement(
                    NewElementNodeProvider.ArgumentBased { args ->
                        elementFromString(
                            "${args[parameters[0]]}(\"${args[parameters[1]]}\")"
                        )!!
                    }
                )
            )
        )
    }
}
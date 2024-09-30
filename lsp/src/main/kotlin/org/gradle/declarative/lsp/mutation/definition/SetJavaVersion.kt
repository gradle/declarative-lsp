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
import org.gradle.declarative.lsp.extension.findPropertyNamed
import org.gradle.declarative.lsp.extension.typeByFqn
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationRequest
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameter
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind
import org.gradle.internal.declarativedsl.dom.mutation.NewValueNodeProvider
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocation
import org.gradle.internal.declarativedsl.dom.mutation.inObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.valueFromString

class SetJavaVersion : MutationDefinition {
    override val id: String = "setJavaVersion"
    override val name: String = "Set Java Version"
    override val description: String = "Set the Java version for the project"
    override val parameters: List<MutationParameter<*>> = listOf(
        MutationParameter(
            "javaVersion",
            "The new Java version.",
            MutationParameterKind.IntParameter
        )
    )

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema
            .dataClassTypesByFqName
            .keys
            .any {
                it.qualifiedName == "org.gradle.api.experimental.jvm.HasJvmApplication"
            }

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> {
        val hasJavaTargetType = projectAnalysisSchema.typeByFqn("org.gradle.api.experimental.jvm.HasJavaTarget")

        return listOf(
            ModelMutationRequest(
                ScopeLocation.inAnyScope().inObjectsOfType(hasJavaTargetType),
                ModelMutation.SetPropertyValue(
                    hasJavaTargetType.findPropertyNamed("javaVersion")!!,
                    NewValueNodeProvider.ArgumentBased {
                        valueFromString("${it[parameters[0]]}")!!
                    }
                )
            )
        )
    }

}
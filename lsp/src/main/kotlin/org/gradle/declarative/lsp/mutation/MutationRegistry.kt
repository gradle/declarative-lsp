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

package org.gradle.declarative.lsp.mutation

import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.util.Ranges
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.lsp.extension.toLspRange
import org.gradle.internal.declarativedsl.dom.mutation.MutationApplicability
import org.gradle.internal.declarativedsl.dom.mutation.MutationApplicabilityChecker
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import java.net.URI

class MutationRegistry(
    private val possibleMutations: List<MutationDefinition>
) {
    private val applicableMutations: MutableMap<URI, List<Pair<MutationDefinition, MutationApplicability>>> =
        mutableMapOf()

    fun getMutationByName(name: String): MutationDefinition? {
        return possibleMutations.firstOrNull { it.id == name }
    }

    fun registerDocument(uri: URI, schema: AnalysisSchema, documentWithResolution: DocumentWithResolution) {
        val applicabilityChecker =
            MutationApplicabilityChecker(schema, documentWithResolution)
        val applicableMutations = possibleMutations.flatMap { mutation ->
            applicabilityChecker.checkApplicability(mutation).map { applicability -> Pair(mutation, applicability) }
        }
        this.applicableMutations[uri] = applicableMutations
    }

    fun getApplicableMutations(uri: URI, queriedRange: Range): List<MutationDefinition> {
        return applicableMutations[uri]?.mapNotNull { (mutation, applicability) ->
            when (applicability) {
                is MutationApplicability.ScopeWithoutAffectedNodes -> mutation
                is MutationApplicability.AffectedNode -> {
                    val nodeRange = applicability.node.sourceData.toLspRange()
                    if (Ranges.containsRange(nodeRange, queriedRange)) {
                        mutation
                    } else {
                        null
                    }
                }
            }
        }.orEmpty()
    }

}
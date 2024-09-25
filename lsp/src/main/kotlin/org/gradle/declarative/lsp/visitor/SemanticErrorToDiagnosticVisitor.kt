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

package org.gradle.declarative.lsp.visitor

import org.eclipse.lsp4j.Diagnostic
import org.gradle.declarative.lsp.extension.toLspRange
import org.gradle.internal.declarativedsl.dom.AmbiguousName
import org.gradle.internal.declarativedsl.dom.BlockMismatch
import org.gradle.internal.declarativedsl.dom.CrossScopeAccess
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.IsError
import org.gradle.internal.declarativedsl.dom.NotAssignable
import org.gradle.internal.declarativedsl.dom.ResolutionFailureReason
import org.gradle.internal.declarativedsl.dom.UnresolvedBase
import org.gradle.internal.declarativedsl.dom.UnresolvedName
import org.gradle.internal.declarativedsl.dom.UnresolvedSignature
import org.gradle.internal.declarativedsl.dom.UnresolvedValueUsed
import org.gradle.internal.declarativedsl.dom.ValueTypeMismatch
import org.gradle.internal.declarativedsl.dom.data.data
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer

class SemanticErrorToDiagnosticVisitor(private val documentOverlayResult: DocumentResolutionContainer): DocumentVisitor() {

    val diagnostics: MutableList<Diagnostic> = mutableListOf()

    override fun visitNode(node: DeclarativeDocument.Node) {
        when (val resolution = documentOverlayResult.data(node)) {
            is DocumentResolution.UnsuccessfulResolution -> {
                diagnostics += resolution.reasons.map {
                    Diagnostic(
                        node.sourceData.toLspRange(),
                        it.toHumanName()
                    )
                }
            }
            is DocumentResolution.SuccessfulResolution -> {}
        }
    }

    private fun ResolutionFailureReason.toHumanName() = when (this) {
        AmbiguousName -> "Ambiguous name"
        BlockMismatch -> "Block mismatch"
        CrossScopeAccess -> "Cross-scope access"
        IsError -> "Error encountered"
        UnresolvedBase -> "Unresolved base"
        UnresolvedName -> "Unresolved name"
        UnresolvedSignature -> "Unresolved signature"
        NotAssignable -> "Not assignable"
        UnresolvedValueUsed -> "Unresolved value used"
        ValueTypeMismatch -> "Value type mismatch"
    }

}
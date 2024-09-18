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
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.gradle.declarative.lsp.toLspRange
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentError
import org.gradle.internal.declarativedsl.dom.SyntaxError
import org.gradle.internal.declarativedsl.dom.UnsupportedKotlinFeature
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntax
import org.gradle.internal.declarativedsl.language.SourceData

/**
 * Visitor finding _syntax_ error nodes in a document.
 */
class SyntaxErrorToDiagnosticVisitor : DocumentVisitor() {

    val diagnostics: MutableList<Diagnostic> = mutableListOf()

    override fun visitDocumentErrorNode(node: DeclarativeDocument.DocumentNode.ErrorNode) {
        diagnostics += node.errors.map { error ->
            domErrorToDiagnostic(
                node.sourceData,
                error
            )
        }
    }

    private fun domErrorToDiagnostic(nodeSourceData: SourceData, error: DocumentError): Diagnostic = when (error) {
        is SyntaxError -> Diagnostic(
            nodeSourceData.toLspRange(),
            error.parsingError.message,
            DiagnosticSeverity.Error,
            "Gradle Declarative DSL"
        )

        is UnsupportedKotlinFeature -> Diagnostic(
            error.unsupportedConstruct.erroneousSource.let {
                Range(
                    Position(0, 0),
                    Position(0, 0)
                )
            },
            // TODO: Make a proper error message
            error.unsupportedConstruct.languageFeature.toString(),
            DiagnosticSeverity.Error,
            "Gradle Declarative DSL"
        )

        is UnsupportedSyntax -> Diagnostic(
            nodeSourceData.toLspRange(),
            error.cause.toString(),
            DiagnosticSeverity.Error,
            "Gradle Declarative DSL"
        )

        else -> throw IllegalArgumentException("Unsupported error type: $error")
    }
}
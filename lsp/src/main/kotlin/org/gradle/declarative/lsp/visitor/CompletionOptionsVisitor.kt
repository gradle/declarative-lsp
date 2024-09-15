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

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentNodeContainer
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer

class CompletionOptionsVisitor(
    private val documentResolutionContainer: DocumentResolutionContainer,
    private val schemaTypeRefContext: SchemaTypeRefContext
) : DocumentNodeVisitor() {

    private var currentContainer: DocumentNodeContainer? = null
    val completions: MutableList<CompletionItem> = mutableListOf()

    override fun visitDocumentNodeContainer(node: DocumentNodeContainer) {
        currentContainer = node
    }

    override fun visitDocumentElementNode(node: DeclarativeDocument.DocumentNode.ElementNode) {
        val alreadyUsedTypes = currentContainer?.content?.map { element ->
            documentResolutionContainer.data(element).let {
                when (it) {
                    is SuccessfulElementResolution -> it.elementType
                    else -> null
                }
            }
        }.orEmpty()

        val resolutionData = documentResolutionContainer.data(node)
        if (resolutionData is SuccessfulElementResolution) {
            val elementTypeRef = resolutionData.elementType
            if (elementTypeRef is DataClass && !alreadyUsedTypes.contains(elementTypeRef)) {
                elementTypeRef.properties.forEach {
                    val completion = CompletionItem(
                        it.name
                    ).apply {
                        kind = CompletionItemKind.Field
                    }

                    completions.add(completion)
                }
            }
        }
    }
}

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

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.util.Ranges
import org.gradle.declarative.lsp.extension.toLspRange
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Visitor capable of finding the node in a document that matches a given cursor position.
 */
class BestFittingNodeVisitor<T: DeclarativeDocument.Node>(
    private val position: Position,
    private val nodeType: KClass<T>
) : DocumentVisitor() {

    /**
     * All the elements leading to the best-fitting node.
     * Might contain the
     */
    var containers: MutableList<DeclarativeDocument.DocumentNode.ElementNode> = mutableListOf()

    /**
     * The node that best fits the cursor position.
     */
    var bestFittingNode: T? = null

    override fun visitNode(node: DeclarativeDocument.Node) {
        val nodeRange = node.sourceData.toLspRange()
        if (nodeType.isInstance(node) && Ranges.containsPosition(nodeRange, position)) {
            bestFittingNode = nodeType.cast(node)
        }
    }

    override fun visitDocumentElementNode(node: DeclarativeDocument.DocumentNode.ElementNode) {
        containers += node
    }

}

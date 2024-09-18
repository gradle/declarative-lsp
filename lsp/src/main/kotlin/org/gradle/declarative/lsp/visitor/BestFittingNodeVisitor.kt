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
import org.gradle.declarative.lsp.toLspRange
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

    /** The node best-fitting the cursor position. */
    var matchingNodes: List<T> = mutableListOf()

    // Property to get the last node
    val matchingNode: T?
        get() = matchingNodes.lastOrNull()

    override fun visitNode(node: DeclarativeDocument.Node) {
        val nodeRange = node.sourceData.toLspRange()
        if (nodeType.isInstance(node) && Ranges.containsPosition(nodeRange, position))
            matchingNodes += nodeType.cast(node)
    }

}

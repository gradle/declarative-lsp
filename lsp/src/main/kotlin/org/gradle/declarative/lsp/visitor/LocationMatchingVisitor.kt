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

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument

/**
 * Visitor capable of finding the node in a document that matches a given cursor position.
 */
class LocationMatchingVisitor(val line: Int, val column: Int) : DocumentNodeVisitor() {

    var matchingNode: DeclarativeDocument.Node? = null

    override fun visitNode(node: DeclarativeDocument.Node) {
        if (isPositionInNode(node, line, column)) {
            matchingNode = node
        }
    }

    companion object {
        private fun isPositionInNode(node: DeclarativeDocument.Node, line: Int, column: Int): Boolean {
            // If we are not in the line range of the node, we can be sure that we are not in the node
            if (!node.sourceData.lineRange.contains(line)) {
                return false
            }

            // In the first and last line, we need to have extra checks for the column
            // Note that in a single line node, the start and end column are the same, so both will be checked
            if (node.sourceData.lineRange.first == line && node.sourceData.startColumn > column) {
                return false
            }
            if (node.sourceData.lineRange.last == line && node.sourceData.endColumn < column) {
                return false
            }

            return true
        }
    }

}

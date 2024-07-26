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

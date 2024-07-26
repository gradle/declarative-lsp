package org.gradle.declarative.lsp.visitor

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentNodeContainer

/**
 * Classic visitor for visiting nodes in a DeclarativeDocument.
 */
open class DocumentNodeVisitor {

    open fun visitNode(node: DeclarativeDocument.Node) {}

    open fun visitDocumentNode(node: DeclarativeDocument.DocumentNode) {}

    open fun visitDocumentElementNode(node: DeclarativeDocument.DocumentNode.ElementNode) {}

    open fun visitDocumentPropertyNode(node: DeclarativeDocument.DocumentNode.PropertyNode) {}

    open fun visitDocumentErrorNode(node: DeclarativeDocument.DocumentNode.ErrorNode) {}

    open fun visitValueNode(node: DeclarativeDocument.ValueNode) {}

    open fun visitValueLiteralNode(node: DeclarativeDocument.ValueNode.LiteralValueNode) {}

    open fun visitValueFactoryNode(node: DeclarativeDocument.ValueNode.ValueFactoryNode) {}
}

/**
 * Extension function to visit all nodes in a DeclarativeDocument.
 *
 * In order to use this, implement a subclass of [DocumentNodeVisitor]
 * and override the methods you need to do the analysis.
 */
fun DeclarativeDocument.visit(visitor: DocumentNodeVisitor) {
    // Initialize the list of nodes to visit with the root nodes of the forest
    val nodesToVisit = this.content.toMutableList()

    while (nodesToVisit.isNotEmpty()) {
        val node = nodesToVisit.removeFirst()

        visitor.visitNode(node)
        if (node is DocumentNodeContainer) {
            nodesToVisit.addAll(node.content)
        }
        when (node) {
            is DeclarativeDocument.DocumentNode -> {
                visitor.visitDocumentNode(node)
                when (node) {
                    is DeclarativeDocument.DocumentNode.ElementNode -> visitor.visitDocumentElementNode(node)
                    is DeclarativeDocument.DocumentNode.ErrorNode -> visitor.visitDocumentErrorNode(node)
                    is DeclarativeDocument.DocumentNode.PropertyNode -> visitor.visitDocumentPropertyNode(node)
                }
            }
            is DeclarativeDocument.ValueNode -> {
                visitor.visitValueNode(node)
                when (node) {
                    is DeclarativeDocument.ValueNode.LiteralValueNode -> visitor.visitValueLiteralNode(node)
                    is DeclarativeDocument.ValueNode.ValueFactoryNode -> visitor.visitValueFactoryNode(node)
                    else -> {}
                }
            }
        }
    }
}

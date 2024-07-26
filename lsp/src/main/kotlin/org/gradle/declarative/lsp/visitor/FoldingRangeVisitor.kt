package org.gradle.declarative.lsp.visitor

import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument

class FoldingRangeVisitor: DocumentNodeVisitor() {

    val foldingRanges: MutableList<FoldingRange> = mutableListOf()

    override fun visitDocumentElementNode(node: DeclarativeDocument.DocumentNode.ElementNode) {
        val range = FoldingRange(
            // 1-based to 0-based
            node.sourceData.lineRange.first - 1,
            // 1-based to 0-based, but DCL is inclusive and LSP is exclusive (hence we subtract 0)
            node.sourceData.lineRange.last,
        ).apply {
            kind = FoldingRangeKind.Region
            startCharacter = node.sourceData.startColumn - 1
            endCharacter = node.sourceData.endColumn - 1
            collapsedText = node.name
        }
        foldingRanges.add(range)
    }

}

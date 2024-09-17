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

import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument

/**
 * Visitor finding and
 */
class FoldingRangeVisitor: DocumentVisitor() {

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

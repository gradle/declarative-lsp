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

package org.gradle.declarative.lsp.extension

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.gradle.internal.declarativedsl.language.SourceData

/**
 * Converts between DCL's [SourceData] and LSP's [Range].
 *
 * The conversion is needed because:
 *  - DCL uses 1-based line and column numbers, while LSP uses 0-based line and column numbers.
 *  - DCL uses inclusive ranges, while LSP uses exclusive ranges.
 */
fun SourceData.toLspRange(): Range {
    val startPosition = Position(
        lineRange.first - 1,
        startColumn - 1
    )
    val endPosition = Position(
        lineRange.last - 1,
        // This missing -1 is intentional, as it makes the end position exclusive
        endColumn
    )
    return Range(startPosition, endPosition)
}
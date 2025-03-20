/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.declarative.lsp.service

import org.gradle.declarative.lsp.computeCompletionLabel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompletionTest {

    @Test
    fun `completion label for generic listOf() function - string elements`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("propListOfStrings", function)
        assertEquals("listOf(vararg elements: String)", function.computeCompletionLabel(typeRefContext, typeSubstitution!!))
    }

    @Test
    fun `completion label for generic listOf() function - list-of-string elements`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("propListOfListOfStrings", function)
        assertEquals("listOf(vararg elements: List<String>)", function.computeCompletionLabel(typeRefContext, typeSubstitution!!))
    }

    @Test
    fun `completion label for generic mapOf() function`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "mapOf" }
        val typeSubstitution = computeTypeSubstitution("propMapOfIntegersToStrings", function)
        assertEquals("mapOf(vararg pairs: Pair<Int, String>)", function.computeCompletionLabel(typeRefContext, typeSubstitution!!))
    }

}
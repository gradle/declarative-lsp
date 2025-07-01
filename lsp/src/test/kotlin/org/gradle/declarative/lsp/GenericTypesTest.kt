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

package org.gradle.declarative.lsp

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GenericTypesTest {

    @Test
    fun `generic listOf() function matches list of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("propListOfStrings", function)
        assertSingleEntryTypeSubstitution(typeSubstitution) {
            assertTrue(it is DataType.StringDataType)
        }
    }

    @Test
    fun `specific funListOfStrings() function matches list of strings`() {
        val function = schema.typeFor<TopLevel>().singleFunctionNamed("funListOfStrings").function
        val typeSubstitution = computeTypeSubstitution("propListOfStrings", function)
        assertEmptyTypeSubstitution(typeSubstitution)
    }

    @Test
    fun `generic listOf() function matches list of lists of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("propListOfListOfStrings", function)
        assertSingleEntryTypeSubstitution(typeSubstitution) {
            it as DataType.ParameterizedTypeInstance
            assertEquals("java.util.List", it.javaTypeName)
            assertEquals("[String]", it.typeArguments.toString())
        }
    }

    @Test
    fun `generic mapOf() function doesn't match list of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "mapOf" }
        val typeSubstitution = computeTypeSubstitution("propListOfStrings", function)
        assertFailedTypeSubstitution(typeSubstitution)
    }

    @Test
    fun `specific funListOfIntegers() function doesn't match list of strings`() {
        val function = schema.typeFor<TopLevel>().singleFunctionNamed("funListOfIntegers").function
        val typeSubstitution = computeTypeSubstitution("propListOfStrings", function)
        assertFailedTypeSubstitution(typeSubstitution)
    }

    private fun assertSingleEntryTypeSubstitution(
        typeSubstitution: Map<DataType.TypeVariableUsage, DataType>?,
        valueMatcher: (DataType) -> Unit
    ) {
        assertNotNull(typeSubstitution)
        assertEquals(1, typeSubstitution.size)
        val entry = typeSubstitution.entries.single()
        assertTrue(entry.key is DataTypeInternal.DefaultTypeVariableUsage)
        valueMatcher(entry.value)
    }

    private fun assertEmptyTypeSubstitution(typeSubstitution: Map<DataType.TypeVariableUsage, DataType>?) {
        assertNotNull(typeSubstitution)
        assertTrue(typeSubstitution.isEmpty())
    }

    private fun assertFailedTypeSubstitution(typeSubstitution: Map<DataType.TypeVariableUsage, DataType>?) {
        assertNull(typeSubstitution)
    }

}

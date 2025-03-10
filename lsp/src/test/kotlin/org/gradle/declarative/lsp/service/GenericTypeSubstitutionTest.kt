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

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.lsp.computeGenericTypeSubstitutionIfAssignable
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.schemaBuilder.FixedTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.kotlinFunction
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GenericTypeSubstitutionTest {

    @Test
    fun `generic listOf() function matches list of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("myStrings", function)
        assertSingleEntryTypeSubstitution(typeSubstitution) {
            assertTrue(it is DataType.StringDataType)
        }
    }

    @Test
    fun `specific myListOfStrings() function matches list of strings`() {
        val function = schema.typeFor<TopLevel>().singleFunctionNamed("myListOfStrings").function
        val typeSubstitution = computeTypeSubstitution("myStrings", function)
        assertEmptyTypeSubstitution(typeSubstitution)
    }

    @Test
    fun `generic listOf() function matches list of lists of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "listOf" }
        val typeSubstitution = computeTypeSubstitution("myListOfListsOfStrings", function)
        assertSingleEntryTypeSubstitution(typeSubstitution) {
            it as DataType.ParameterizedTypeInstance
            assertEquals("java.util.List", it.javaTypeName)
            assertEquals("[String]", it.typeArguments.toString())
        }
    }

    @Test
    fun `generic mapOf() function doesn't match list of strings`() {
        val function = schema.externalFunctionsByFqName.values.single { it.simpleName == "mapOf" }
        val typeSubstitution = computeTypeSubstitution("myStrings", function)
        assertFailedTypeSubstitution(typeSubstitution)
    }

    @Test
    fun `specific myListOfIntegers() function doesn't match list of strings`() {
        val function = schema.typeFor<TopLevel>().singleFunctionNamed("myListOfIntegers").function
        val typeSubstitution = computeTypeSubstitution("myStrings", function)
        assertFailedTypeSubstitution(typeSubstitution)
    }

    private fun computeTypeSubstitution(propertyName: String, function: SchemaFunction): Map<DataType.TypeVariableUsage, DataType>? {
        val property = schema.typeFor<TopLevel>().propertyNamed(propertyName).property
        val typeSubstitution = with(SchemaTypeRefContext(schema)) {
            computeGenericTypeSubstitutionIfAssignable(
                resolveRef(property.valueType),
                resolveRef(function.returnValueType)
            )
        }
        return typeSubstitution
    }

    private fun assertSingleEntryTypeSubstitution(typeSubstitution: Map<DataType.TypeVariableUsage, DataType>?, valueMatcher: (DataType) -> Unit) {
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

    private val schema = schemaFromTypes(
        TopLevel::class,
        listOf(TopLevel::class, List::class),
        externalFunctionDiscovery = FixedTopLevelFunctionDiscovery(
            listOf(
                Class.forName("kotlin.collections.CollectionsKt").methods.single { it.name == "listOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!,
                Class.forName("kotlin.collections.MapsKt").methods.single { it.name == "mapOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!,
            )
        ),
        defaultImports = listOf(DefaultFqName.parse("kotlin.collections.listOf"))
    )

    @Suppress("unused")
    class TopLevel {
        @get:Restricted
        var myStrings: List<String> = emptyList()

        @get:Restricted
        var myListOfListsOfStrings: List<List<String>> = emptyList()

        @Restricted
        fun myListOfStrings(t1: String, t2: String): List<String> = listOf(t1, t2)

        @Restricted
        fun myListOfIntegers(t1: Int, t2: Int): List<Int> = listOf(t1, t2)
    }

}
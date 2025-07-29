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

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.schemaBuilder.FixedTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import kotlin.reflect.jvm.kotlinFunction


internal fun computeTypeSubstitution(propertyName: String, function: SchemaFunction): Map<DataType.TypeVariableUsage, DataType>? {
    val property = schema.typeFor<TopLevel>().propertyNamed(propertyName).property
    val typeSubstitution = with(typeRefContext) {
        computeGenericTypeSubstitutionIfAssignable(
            resolveRef(property.valueType),
            resolveRef(function.returnValueType)
        )
    }
    return typeSubstitution
}

internal val schema = schemaFromTypes(
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

internal val typeRefContext = SchemaTypeRefContext(schema)

@Suppress("unused")
class TopLevel {
    @get:Restricted
    var propListOfStrings: List<String> = emptyList()

    @get:Restricted
    var propListOfListOfStrings: List<List<String>> = emptyList()

    @get:Restricted
    var propMapOfIntegersToStrings: Map<Int, String> = emptyMap()

    @Restricted
    fun funListOfStrings(t1: String, t2: String): List<String> = listOf(t1, t2)

    @Restricted
    fun funListOfIntegers(t1: Int, t2: Int): List<Int> = listOf(t1, t2)
}

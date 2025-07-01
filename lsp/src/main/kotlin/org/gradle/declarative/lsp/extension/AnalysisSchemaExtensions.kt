package org.gradle.declarative.lsp.extension

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultEnumClass
import org.gradle.internal.declarativedsl.analysis.DefaultFqName

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

inline fun <reified T> AnalysisSchema.findType(name: String): T? = dataClassTypesByFqName
    .values
    .find { dataClass ->
        dataClass.name.qualifiedName == name && dataClass is T
    }?.let {
        it as T
    }


/**
 * Produces an [AnalysisSchema] that approximates the [schemas] merged together.
 * Namely, it has [AnalysisSchema.dataClassTypesByFqName] from all the schemas, and if a type appears in more than
 * one of the schemas, its contents get merged, too.
 *
 * The top level receiver is either the merged type from the [AnalysisSchema.topLevelReceiverType]s from the schemas, if
 * it has the same name in all of them, or a type with a synthetic name that has the content from the top level
 * receiver types from [schemas].
 */
fun unionAnalysisSchema(schemas: List<AnalysisSchema>): AnalysisSchema = if (schemas.size == 1)
    schemas.single()
else {
    fun mergeDataClasses(newName: String?, dataClasses: List<DataClass>): DataClass {
        // Can't have properties with the same name but different types anyway:
        val properties = dataClasses.flatMap { it.properties }.distinctBy { it.name }

        val functions = dataClasses.flatMap { it.memberFunctions }
            .distinctBy { listOf(it.simpleName) + it.parameters.map { param -> param.name to typeIdentityName(param.type) } }

        val constructors =
            dataClasses.flatMap { it.constructors }.distinctBy { it.parameters.map { typeIdentityName(it.type) } }

        val supertypes = dataClasses.flatMap { it.supertypes }.toSet()

        return DefaultDataClass(
            newName?.let { DefaultFqName.parse(it) } ?: dataClasses.first().name,
            dataClasses.first().javaTypeName,
            dataClasses.first().javaTypeArgumentTypeNames,
            supertypes,
            properties,
            functions,
            constructors
        )
    }

    val dataClassesByFqName = run {
        fun mergeEnums(enumTypes: List<EnumClass>): EnumClass =
            DefaultEnumClass(
                enumTypes.first().name,
                enumTypes.first().javaTypeName,
                enumTypes.flatMap { it.entryNames }.distinct()
            )

        schemas.flatMap { it.dataClassTypesByFqName.values }.groupBy { it.name }
            .mapValues { (_, dataClasses) ->
                when {
                    dataClasses.all { it is DataClass } -> mergeDataClasses(null, dataClasses.map { it as DataClass })
                    dataClasses.all { it is EnumClass } -> mergeEnums(dataClasses.map { it as EnumClass })
                    else -> error("mixed enum and data classes")
                }
            }
    }

    val newTopLevelReceiver = run {
        val topLevelReceivers = schemas.map { it.topLevelReceiverType }
        if (topLevelReceivers.map { it.name.qualifiedName }.distinct().size == 1) {
            dataClassesByFqName.getValue(topLevelReceivers.first().name) as DataClass
        } else {
            mergeDataClasses("\$top-level-receiver\$", topLevelReceivers)
        }
    }

    val newExternalFunctionsByFqName = run {
        val mergedResult = mutableMapOf<FqName, DataTopLevelFunction>()
        schemas.forEach { schema ->
            mergedResult.putAll(schema.externalFunctionsByFqName)
        }
        mergedResult
    }

    val genericInstantiationsByFqName = run {
        val mergedResult =
            mutableMapOf<FqName, Map<List<DataType.ParameterizedTypeInstance.TypeArgument>, DataType.ClassDataType>>()
        schemas.forEach { schema ->
            schema.genericInstantiationsByFqName.forEach { instantiation ->
                mergedResult.merge(instantiation.key, instantiation.value) { oldVal, newVal -> oldVal + newVal }
            }
        }
        mergedResult
    }

    val augmentations =
        schemas.flatMap { it.assignmentAugmentationsByTypeName.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, value) -> value.flatten() }

    DefaultAnalysisSchema(
        newTopLevelReceiver,
        dataClassesByFqName,
        emptyMap(),
        genericInstantiationsByFqName,
        newExternalFunctionsByFqName,
        emptyMap(),
        schemas.flatMapTo(mutableSetOf()) { it.defaultImports },
        augmentations
    )
}

private fun typeIdentityName(typeRef: DataTypeRef) = when (typeRef) {
    is DataTypeRef.Name -> typeRef.fqName.qualifiedName
    is DataTypeRef.Type -> when (val type = typeRef.dataType) {
        is DataType.ClassDataType -> type.name.qualifiedName
        else -> type.toString()
    }
    is DataTypeRef.NameWithArgs -> TODO()
}

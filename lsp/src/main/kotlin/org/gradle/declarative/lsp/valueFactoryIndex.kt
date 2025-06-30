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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.analysis.TypeRefContext

class ValueFactoryIndex(storeEntry: VersionedDocumentStore.DocumentStoreEntry) {

    private val index: Map<FqName, List<ValueFactoryIndexEntry>> by lazy {
        indexValueFactories(storeEntry.unionSchema, storeEntry.typeRefContext)
    }

    // TODO: currently this index contains all value factories from the whole schema
    //  going forward we should make it take into account which value factory is available
    //  in which block, because only the ones defined at the top level are available everywhere

    fun factoriesForProperty(fqName: FqName): List<ValueFactoryIndexEntry>? = index[fqName]

    private fun indexValueFactories(schema: AnalysisSchema, typeRefContext: TypeRefContext): Map<FqName, List<ValueFactoryIndexEntry>> {
        val factoryIndex = mutableMapOf<FqName, List<ValueFactoryIndexEntry>>()
        factoryIndex.merge(indexValueFactoriesFromExternalFunctions(schema))
        factoryIndex.merge(indexValueFactoriesFromTopLevelReceiver(schema, typeRefContext))
        return factoryIndex
    }

    private fun indexValueFactoriesFromExternalFunctions(schema: AnalysisSchema): Map<FqName, List<ValueFactoryIndexEntry>> {
        val factoryIndex = mutableMapOf<FqName, List<ValueFactoryIndexEntry>>()
        schema.externalFunctionsByFqName
            .map { it.value }
            .filter { isValueFactoryFunction(it) }
            .forEach {
                val indexKey = returnTypeOfValueFactoryFunction(it)
                val valueFactoryIndexEntry = ValueFactoryIndexEntry(it, "")
                factoryIndex.merge(indexKey, listOf(valueFactoryIndexEntry)) { oldVal, newVal -> oldVal + newVal }
            }
        return factoryIndex
    }

    private fun indexValueFactoriesFromTopLevelReceiver(schema: AnalysisSchema, typeRefContext: TypeRefContext) =
        indexValueFactories(typeRefContext, schema.topLevelReceiverType, "")

    private fun indexValueFactories(
        typeRefContext: TypeRefContext,
        type: DataClass,
        namePrefix: String
    ): Map<FqName, List<ValueFactoryIndexEntry>> {
        val factoryIndex = mutableMapOf<FqName, List<ValueFactoryIndexEntry>>()

        type.memberFunctions
            .filter { isValueFactoryFunction(it) }
            .forEach {
                val indexKey = returnTypeOfValueFactoryFunction(it)
                val valueFactoryIndexEntry = ValueFactoryIndexEntry(it, namePrefix)
                factoryIndex.merge(indexKey, listOf(valueFactoryIndexEntry)) { oldVal, newVal -> oldVal + newVal }
            }

        type.properties.forEach {
            when (val propType = typeRefContext.resolveRef(it.valueType)) {
                is DataClass -> {
                    val propName = it.name
                    val propIndex = indexValueFactories(typeRefContext, propType, "$namePrefix${propName}.")
                    factoryIndex.merge(propIndex)
                }

                else -> Unit
            }
        }

        return factoryIndex
    }

    private fun isValueFactoryFunction(function: SchemaFunction) =
        function.semantics is FunctionSemantics.Pure && (function.returnValueType is DataTypeRef.Name || function.returnValueType is DataTypeRef.NameWithArgs)

    private fun returnTypeOfValueFactoryFunction(it: SchemaFunction) =
        when (val returnType = it.returnValueType) {
            is DataTypeRef.Name -> returnType.fqName
            is DataTypeRef.NameWithArgs -> returnType.fqName
            is DataTypeRef.Type -> error("unexpected return type")
        }
}

data class ValueFactoryIndexEntry(
    val function: SchemaFunction,
    val namePrefix: String
)

private fun <K, E> MutableMap<K, List<E>>.merge(other: Map<K, List<E>>) {
    other.forEach { (key, value) -> this.merge(key, value) { oldVal, newVal -> oldVal + newVal } }
}
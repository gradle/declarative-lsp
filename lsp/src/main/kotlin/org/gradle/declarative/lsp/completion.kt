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
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.analysis.TypeRefContext


internal fun SchemaFunction.computeCompletionLabel(
    typeRefContext: TypeRefContext,
    genericTypeSubstitution: Map<DataType.TypeVariableUsage, DataType> = emptyMap()
): String {
    val parameterSignature = when (parameters.isEmpty()) {
        true -> ""
        false -> parameters
            .joinToString(",", "(", ")") { it.toSignatureLabel(typeRefContext, genericTypeSubstitution) }
    }
    val configureBlockLabel = semantics.toBlockConfigurabilityLabel().orEmpty()

    return "$simpleName$parameterSignature$configureBlockLabel"
}

internal fun SchemaFunction.computeCompletionInsertText(typeRefContext: TypeRefContext): String {
    val parameterSnippet = parameters.mapIndexed { index, parameter ->
        // Additional placeholders are indexed from 1
        val resolvedType = typeRefContext.resolveRef(parameter.type)
        computeTypedPlaceholder(index + 1, resolvedType)
    }.joinToString(", ", "(", ")")

    return when (val semantics = semantics) {
        is FunctionSemantics.ConfigureSemantics -> {
            when (semantics.configureBlockRequirement) {
                is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> {
                    if (parameters.isEmpty()) {
                        // Mandatory configuration without function parameters can omit the parentheses
                        "$simpleName {\n\t$0\n}"
                    } else {
                        // Otherwise, we need the parentheses with the parameter snippets
                        "$simpleName${parameterSnippet} {\n\t$0\n}"
                    }
                }

                else -> {
                    // Optional configuration can be omitted
                    "$simpleName${parameterSnippet}$0"
                }
            }
        }

        else -> "$simpleName${parameterSnippet}$0"
    }
}

/**
 * Computes a [placeholder](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#placeholders)
 * based on the given data type.
 *
 * If there is a specific placeholder for the given data type, it will be used.
 * Otherwise, a simple indexed will be used
 */
internal fun computeTypedPlaceholder(
    index: Int,
    resolvedType: DataType
): String {
    return when (resolvedType) {
        is DataType.BooleanDataType -> "\${$index|true,false|}"
        is EnumClass -> "\${$index|${resolvedType.entryNames.joinToString(",")}|}"
        is DataType.IntDataType -> "\${$index:0}"
        is DataType.LongDataType -> "\${$index:0L}"
        is DataType.StringDataType -> "\"\${$index}\""
        else -> "\$$index"
    }
}

private fun FunctionSemantics.toBlockConfigurabilityLabel(): String? = when (this) {
    is FunctionSemantics.ConfigureSemantics -> when (configureBlockRequirement) {
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> " { this: ${configuredType.toSimpleName()} }"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Optional -> " { this: ${configuredType.toSimpleName()} } (optional configuration)"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NotAllowed -> null
    }

    else -> null
}
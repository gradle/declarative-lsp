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
import org.gradle.internal.declarativedsl.analysis.TypeRefContext

// TODO: where should this utility code live

internal fun TypeRefContext.computeGenericTypeSubstitutionIfAssignable(
    expectedType: DataType,
    actualType: DataType
): Map<DataType.TypeVariableUsage, DataType>? {
    var hasConflict = false
    val result = buildMap {
        fun recordEquivalence(typeVariableUsage: DataType.TypeVariableUsage, otherType: DataType) {
            val mapping = getOrPut(typeVariableUsage) { otherType }
            if (mapping != otherType) {
                hasConflict = true
            }
        }

        fun visitTypePair(left: DataType, right: DataType) {
            when {
                left is DataType.TypeVariableUsage -> recordEquivalence(left, right)
                right is DataType.TypeVariableUsage -> recordEquivalence(right, left)

                left is DataType.ParameterizedTypeInstance || right is DataType.ParameterizedTypeInstance -> {
                    if (left is DataType.ParameterizedTypeInstance && right is DataType.ParameterizedTypeInstance && left.typeArguments.size == right.typeArguments.size) {
                        left.typeArguments.zip(right.typeArguments).forEach { (leftArg, rightArg) ->
                            when (leftArg) {
                                is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument -> {
                                    if (rightArg is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument) {
                                        visitTypePair(resolveRef(leftArg.type), resolveRef(rightArg.type))
                                    } else {
                                        hasConflict = true
                                    }
                                }

                                is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection -> Unit
                            }
                        }
                    } else {
                        hasConflict = true
                    }
                }

                !sameType(left, right) -> hasConflict = true
            }
        }

        visitTypePair(expectedType, actualType)
    }
    return result.takeIf { !hasConflict }
}


/**
 * Can't check for equality: TAPI proxies are not equal to the original implementations.
 */
private fun TypeRefContext.sameType(left: DataType, right: DataType): Boolean = when (left) {
    is DataType.ParameterizedTypeInstance -> right is DataType.ParameterizedTypeInstance &&
            with(left.typeArguments.zip(right.typeArguments)) {
                size == left.typeArguments.size &&
                        all { (leftArg, rightArg) ->
                            when (leftArg) {
                                is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection -> rightArg is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection
                                is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument -> rightArg is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument && sameType(
                                    resolveRef(leftArg.type),
                                    resolveRef(rightArg.type)
                                )
                            }
                        }
            }

    is DataType.ClassDataType -> right is DataType.ClassDataType && left.name.qualifiedName == right.name.qualifiedName
    is DataType.BooleanDataType -> right is DataType.BooleanDataType
    is DataType.IntDataType -> right is DataType.IntDataType
    is DataType.LongDataType -> right is DataType.LongDataType
    is DataType.StringDataType -> right is DataType.StringDataType
    is DataType.NullType -> right is DataType.NullType
    is DataType.UnitType -> right is DataType.UnitType
    is DataType.TypeVariableUsage -> right is DataType.TypeVariableUsage && left.variableId == right.variableId
}


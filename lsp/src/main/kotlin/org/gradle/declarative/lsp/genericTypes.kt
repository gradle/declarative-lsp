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

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.VarargParameter
import org.gradle.internal.declarativedsl.analysis.TypeArgumentInternal
import org.gradle.internal.declarativedsl.analysis.TypeRefContext
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.language.DataTypeInternal

internal fun DataTypeRef.toSimpleName(genericTypeSubstitution: (DataType) -> DataType = ::identity): String =
    when (this) {
        is DataTypeRef.Name -> fqName.simpleName
        is DataTypeRef.NameWithArgs -> "${fqName.simpleName}<${typeArguments.joinToString { it.toSimpleName(genericTypeSubstitution) }}>"
        is DataTypeRef.Type -> dataType.toString()
    }

internal fun DataType.ParameterizedTypeInstance.TypeArgument.toSimpleName(genericTypeSubstitution: (DataType) -> DataType) =
    when (this) {
        is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument ->
            if (type is DataTypeRef.Type && (type as DataTypeRef.Type).dataType is DataType.TypeVariableUsage) {
                val inputType = (type as DataTypeRef.Type).dataType
                val outputType = genericTypeSubstitution(inputType)
                outputType.toString()
            } else {
                this.toString()
            }

        is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection -> this.toString()
    }


internal fun SchemaFunction.toSignatureLabel(typeRefContext: TypeRefContext): String {
    val parameterSignatures = this.parameters.joinToString(", ") { parameter ->
        parameter.toSignatureLabel(typeRefContext)
    }

    return "${this.simpleName}(${parameterSignatures})"
}

internal fun DataParameter.toSignatureLabel(typeRefContext: TypeRefContext, genericTypeSubstitution: Map<DataType.TypeVariableUsage, DataType> = emptyMap()): String {
    val genericType = typeRefContext.resolveRef(this.type)
    val concreteType = typeRefContext.applyTypeSubstitution(genericType, genericTypeSubstitution)
    return when (this) {
        is VarargParameter -> "vararg ${this.name}: ${concreteType.getVarargTypeArgument()}"
        else -> "${this.name}: $concreteType"
    }
}

private fun DataType.getVarargTypeArgument(): DataTypeRef {
    require(this is DataType.ParameterizedTypeInstance)
    require(this.typeArguments.size == 1)
    require(this.typeArguments[0] is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument)
    return (this.typeArguments[0] as DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument).type
}


internal fun TypeRefContext.applyTypeSubstitution(
    type: DataType,
    substitution: Map<DataType.TypeVariableUsage, DataType>
): DataType {
    // TODO: copied from Gradle codebase, where it's internal

    fun substituteInTypeArgument(typeArgument: DataType.ParameterizedTypeInstance.TypeArgument) = when (typeArgument) {
        is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument -> TypeArgumentInternal.DefaultConcreteTypeArgument(applyTypeSubstitution(resolveRef(typeArgument.type), substitution).ref)
        is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection -> typeArgument
    }

    return when (type) {
        is DataType.ParameterizedTypeInstance -> DataTypeInternal.DefaultParameterizedTypeInstance(type.typeSignature, type.typeArguments.map(::substituteInTypeArgument))
        is DataType.TypeVariableUsage -> substitution[type] ?: type

        is DataClass,
        is EnumClass,
        is DataType.ConstantType<*>,
        is DataType.NullType,
        is DataType.UnitType -> type
    }
}


internal fun TypeRefContext.computeGenericTypeSubstitutionIfAssignable( // TODO: where should this utility code live
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

internal fun <T> identity(t: T) = t


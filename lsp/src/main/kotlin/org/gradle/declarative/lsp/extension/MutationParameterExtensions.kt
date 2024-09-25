package org.gradle.declarative.lsp.extension

import org.gradle.internal.declarativedsl.dom.mutation.MutationParameter
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind

fun MutationParameter<*>.toJsonMap(): Map<String, String> {
    return mapOf(
        "name" to name,
        "description" to description,
        "kind" to when(kind) {
            is MutationParameterKind.StringParameter -> "string"
            is MutationParameterKind.BooleanParameter -> "boolean"
            is MutationParameterKind.IntParameter -> "int"
        }
    )
}
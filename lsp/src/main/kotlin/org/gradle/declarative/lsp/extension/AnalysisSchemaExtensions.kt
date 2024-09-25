package org.gradle.declarative.lsp.extension

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember

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

fun AnalysisSchema.typeByFqn(name: String): DataClass =
    dataClassesByFqName.entries.single { it.key.qualifiedName == name }.value

val AnalysisSchema.lintEnabled: TypedMember.TypedProperty
    get() = typeByFqn("org.gradle.api.experimental.common.extensions.Lint").propertyNamed("enabled")

val AnalysisSchema.hasLibraryDependencies: DataClass
    get() = typeByFqn("org.gradle.api.experimental.common.HasLibraryDependencies")

val AnalysisSchema.hasApplicationDependencies: DataClass
    get() = typeByFqn("org.gradle.api.experimental.common.HasApplicationDependencies")

fun AnalysisSchema.hasCommonPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.common.LibraryDependencies" }

fun AnalysisSchema.hasCommonDependencies(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.common.HasLibraryDependencies" }
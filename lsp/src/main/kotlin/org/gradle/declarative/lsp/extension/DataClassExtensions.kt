package org.gradle.declarative.lsp.extension

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

fun DataClass.findPropertyNamed(name: String): TypedMember.TypedProperty? =
    properties.find { it.name == name }?.let { TypedMember.TypedProperty(this, it) }

fun DataClass.findMethodNamed(name: String): TypedMember.TypedFunction? =
    memberFunctions.find { it.simpleName == name }?.let { TypedMember.TypedFunction(this, it) }
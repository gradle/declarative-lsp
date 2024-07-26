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

package org.gradle.declarative.lsp.build.model;

import org.gradle.declarative.dsl.evaluation.InterpretationSequence;
import org.gradle.declarative.dsl.schema.AnalysisSchema;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Model holding all required information to make a DOM out of the declarative resources in the build.
 */
public interface ResolvedDeclarativeResourcesModel extends Serializable {
    
    InterpretationSequence getSettingsInterpretationSequence();
    
    InterpretationSequence getProjectInterpretationSequence();

    AnalysisSchema getAnalysisSchema();

    File getRootDir();
    
    File getSettingsFile();

    List<File> getDeclarativeBuildFiles();
}

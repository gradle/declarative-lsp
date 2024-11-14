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

package org.gradle.declarative.lsp.build.action;

import org.gradle.declarative.dsl.evaluation.InterpretationSequence;
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel;
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;

public class GetDeclarativeResourcesModel implements BuildAction<DeclarativeResourcesModel> {

    @Override
    public DeclarativeResourcesModel execute(BuildController controller) {
        DeclarativeSchemaModel declarativeSchemaModel = controller.getModel(DeclarativeSchemaModel.class);
        InterpretationSequence settingsSchema = declarativeSchemaModel.getSettingsSequence();
        InterpretationSequence projectSchema = declarativeSchemaModel.getProjectSequence();
        File rootDir = getRootDir(controller);
        return new DeclarativeResourcesModelImpl(settingsSchema, projectSchema, rootDir);
    }

    private static File getRootDir(BuildController controller) {
        GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
        return gradleBuild.getRootProject().getProjectDirectory();
    }

    
    private static final class DeclarativeResourcesModelImpl implements DeclarativeResourcesModel {

        private final InterpretationSequence settingsSequence;
        private final InterpretationSequence projectSequence;
        private final File rootDir;

        public DeclarativeResourcesModelImpl(
                InterpretationSequence settingsSequence,
                InterpretationSequence projectSequence,
                File rootDir
        ) {
            this.settingsSequence = settingsSequence;
            this.projectSequence = projectSequence;
            this.rootDir = rootDir;
        }

        @Override
        public InterpretationSequence getSettingsInterpretationSequence() {
            return settingsSequence;
        }

        @Override
        public InterpretationSequence getProjectInterpretationSequence() {
            return projectSequence;
        }

        @Override
        public File getRootDir() {
            return rootDir;
        }
    }
}
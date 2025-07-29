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
import org.gradle.tooling.model.GradleProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class GetDeclarativeResourcesModel implements BuildAction<DeclarativeResourcesModel> {

    @Override
    public DeclarativeResourcesModel execute(BuildController controller) {
        DeclarativeSchemaModel declarativeSchemaModel = controller.getModel(DeclarativeSchemaModel.class);
        InterpretationSequence settingsSchema = declarativeSchemaModel.getSettingsSequence();
        InterpretationSequence projectSchema = declarativeSchemaModel.getProjectSequence();
        List<File> projectBuildFiles = getProjectBuildFiles(controller);
        return new DeclarativeResourcesModelImpl(settingsSchema, projectSchema, projectBuildFiles);
    }

    private List<File> getProjectBuildFiles(BuildController controller) {
        GradleProject project = controller.getModel(GradleProject.class);
        List<File> buildFiles = new ArrayList<>();

        // DFS of the project hierarchy to collect build script files
        Stack<GradleProject> projectStack = new Stack<>();
        projectStack.push(project);
        while (!projectStack.isEmpty()) {
            GradleProject currentProject = projectStack.pop();
            currentProject.getChildren().forEach(projectStack::push);

            if (currentProject.getBuildScript().getSourceFile() != null) {
                buildFiles.add(currentProject.getBuildScript().getSourceFile());
            }
        }

        return buildFiles;
    }

    private static final class DeclarativeResourcesModelImpl implements DeclarativeResourcesModel {

        private final InterpretationSequence settingsSequence;
        private final InterpretationSequence projectSequence;
        private final List<File> buildScriptFiles;

        public DeclarativeResourcesModelImpl(
                InterpretationSequence settingsSequence,
                InterpretationSequence projectSequence,
                List<File> buildScriptFiles
        ) {
            this.settingsSequence = settingsSequence;
            this.projectSequence = projectSequence;
            this.buildScriptFiles = buildScriptFiles;
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
        public List<File> getBuildScriptFiles() {
            return Collections.unmodifiableList(buildScriptFiles);
        }

    }
}

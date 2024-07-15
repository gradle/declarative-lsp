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

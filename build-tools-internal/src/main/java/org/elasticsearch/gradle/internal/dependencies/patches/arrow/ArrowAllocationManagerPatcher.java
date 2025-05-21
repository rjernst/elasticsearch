/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencies.patches.arrow;

import org.elasticsearch.gradle.internal.dependencies.patches.PatcherInfo;
import org.elasticsearch.gradle.internal.dependencies.patches.Utils;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.elasticsearch.gradle.internal.dependencies.patches.PatcherInfo.classPatcher;

@CacheableTransform
public abstract class ArrowAllocationManagerPatcher implements TransformAction<TransformParameters.None> {

    //private static final Logger logger = LoggerFactory.getLogger(ArrowAllocationManagerPatcher.class);

    private static final String JAR_FILE_TO_PATCH = "arrow-memory-core";

    private static final List<PatcherInfo> CLASS_PATCHERS = List.of(
        classPatcher(
            "org/apache/arrow/memory/DefaultAllocationManagerOption.class",
            "fd82559880d01055a6cf3f8469a2ea0990134d792fc29694fabfb960be88f6cf",
            DefaultAllocationManagerOptionPatcher::new
        )
    );

    @Classpath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        File inputFile = getInputArtifact().get().getAsFile();

        if (inputFile.getName().startsWith(JAR_FILE_TO_PATCH)) {
            System.out.println("Patching " + inputFile.getName());
            File outputFile = outputs.file(inputFile.getName().replace(".jar", "-patched.jar"));
            Utils.patchJar(inputFile, outputFile, CLASS_PATCHERS);
        } else {
            //System.out.println("Skipping " + inputFile.getName());
            outputs.file(getInputArtifact());
        }
    }
}

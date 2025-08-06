/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.classscanner;

import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.tooling.BuildException;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class ClassScannerWorker implements WorkAction<ClassScannerWorker.Parameters> {


    public interface Parameters extends WorkParameters {
        ListProperty<String> getClassFiles();
        ListProperty<String> getVisitorClasses();
        ListProperty<String> getOutputFiles();
    }

    @Override
    public void execute() {
        List<Path> classFiles = convertPathList(getParameters().getClassFiles());
        List<StatefulClassVisitor> visitors = getParameters().getVisitorClasses().get().stream().map(this::constructVisitor).toList();
        List<Path> outputFiles = convertPathList(getParameters().getOutputFiles());
        if (outputFiles.size() != visitors.size()) {
            throw new IllegalArgumentException("Expected " + visitors.size() + " output files but got " + outputFiles.size());
        }

        ClassVisitor delegatingVisitor = DelegatingClassVisitor.maybeWrap(visitors);
        for (Path classFile : classFiles) {
            try (var classBytes = Files.newInputStream(classFile)) {
                ClassReader classReader = new ClassReader(classBytes);
                classReader.accept(delegatingVisitor, 0);
            } catch (IOException|IllegalArgumentException e) {
                throw new BuildException("Failed to scan file [" + classFile + "]", e);
            }
        }

        for (int i = 0; i < outputFiles.size(); i++) {
            Path outputFile = outputFiles.get(i);
            StatefulClassVisitor classVisitor = visitors.get(i);
            try {
                classVisitor.writeState(outputFile);
            } catch (IOException e) {
                throw new BuildException("Failed to write state file [" + outputFile + "]", e);
            }
        }
    }

    private List<Path> convertPathList(ListProperty<String> input) {
        return input.get().stream().map(Paths::get).toList();
    }

    private StatefulClassVisitor constructVisitor(String classname) {
        try {
            var clazz = Class.forName(classname).asSubclass(StatefulClassVisitor.class);
            return clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + classname + " not found", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Class " + classname + " must have a no-arg constructor", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct " + classname, e);
        }
    }


}

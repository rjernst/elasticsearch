/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.classscanner;

import org.elasticsearch.asm.DelegatingClassVisitor;
import org.elasticsearch.asm.StatefulClassVisitor;
import org.gradle.api.provider.ListProperty;
import org.gradle.tooling.BuildException;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public abstract class ClassScannerWorker implements WorkAction<ClassScannerWorker.Parameters> {

    public interface Parameters extends WorkParameters {
        ListProperty<String> getClassFiles();
        ListProperty<String> getVisitorClassNames();
        ListProperty<String> getOutputFiles();
    }

    @Override
    public void execute() {
        List<Path> classFiles = convertPathList(getParameters().getClassFiles());
        List<String> visitorClassnames = getParameters().getVisitorClassNames().get();
        List<Path> outputFiles = convertPathList(getParameters().getOutputFiles());
        if (outputFiles.size() != visitorClassnames.size()) {
            throw new IllegalArgumentException("Expected " + visitorClassnames.size() + " output files but got " + outputFiles.size());
        }
        List<ClassVisitor> visitors = new ArrayList<>();
        for (String classname : visitorClassnames) {
            visitors.add(constructVisitor(classname));
        }

        ClassVisitor delegatingVisitor = DelegatingClassVisitor.maybeWrap(Opcodes.ASM9, visitors);
        for (Path classFile : classFiles) {
            try (var classBytes = Files.newInputStream(classFile)) {
                ClassReader classReader = new ClassReader(classBytes);
                classReader.accept(delegatingVisitor, 0);
            } catch (IOException|IllegalArgumentException e) {
                throw new BuildException("Failed to scan file [" + classFile + "]", e);
            }
        }

        for (int i = 0; i < outputFiles.size(); i++) {
            ClassVisitor visitor = visitors.get(i);
            Path outputFile = outputFiles.get(i);
            try {
                assert visitor instanceof StatefulClassVisitor;
                ((StatefulClassVisitor) visitor).writeState(outputFile);
            } catch (IOException e) {
                throw new BuildException("Failed to write state file [" + outputFiles.get(i) + "]", e);
            }
        }
    }

    private List<Path> convertPathList(ListProperty<String> input) {
        return input.get().stream().map(Paths::get).toList();
    }

    private ClassVisitor constructVisitor(String classname) {
        ClassVisitor visitor;
        try {
            var clazz = Class.forName(classname).asSubclass(ClassVisitor.class);
            visitor = clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class [" + classname + "] not found", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Class [" + classname + "] must have a single arg constructor taking the output Path", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not construct [" + classname + "]", e);
        }
        if (visitor instanceof StatefulClassVisitor == false) {
            throw new IllegalStateException("Class [" + classname + "] must implement StatefulClassVisitor to write state when closed");
        }
        return visitor;
    }


}

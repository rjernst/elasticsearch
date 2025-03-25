/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugin.scanner;

import org.elasticsearch.plugin.Component;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ManifestBuilder {

    // main method to be used by gradle build plugin
    public static void main(String[] args) throws IOException {
        List<ClassReader> classReaders = ClassReaders.ofClassPath();

        List<String> components = findComponents(classReaders);
        Path outputFile = Path.of(args[0]);
        ManifestBuilder.writeToFile(components, outputFile);
    }

    public static void writeToFile(List<String> components, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            try (XContentBuilder namedComponents = XContentFactory.jsonBuilder(outputStream)) {
                namedComponents.startObject();
                namedComponents.array("components", components.toArray(new String[0]));
                namedComponents.endObject();
            }
        }

    }

    public static List<String> findComponents(List<ClassReader> classReaders) {

        ClassScanner componentScanner = new ClassScanner(Type.getDescriptor(Component.class), (path, map) -> {
            String classname = pathToClassName(path);
            map.put(classname, classname);
            return null;
        });
        componentScanner.visit(classReaders);
        // quick hack to reuse ClassScanner
        return componentScanner.getFoundClasses().keySet().stream().sorted().toList();
    }

    private static String pathToClassName(String classWithSlashes) {
        return classWithSlashes.replace('/', '.');
    }

}

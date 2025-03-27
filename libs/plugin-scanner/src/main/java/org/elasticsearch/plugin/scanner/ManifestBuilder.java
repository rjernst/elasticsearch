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
import org.elasticsearch.plugin.Extension;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ManifestBuilder {

    // main method to be used by gradle build plugin
    public static void main(String[] args) throws IOException {
        List<ClassReader> classReaders = ClassReaders.ofClassPath();

        List<String> componentsClasses = findComponents(classReaders);
        List<String> extensionsFields = findExtensionsFields(classReaders);
        Path outputFile = Path.of(args[0]);
        ManifestBuilder.writeToFile(componentsClasses, extensionsFields, outputFile);
    }

    public static void writeToFile(List<String> componentsClasses, List<String> extensionsFields, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            try (XContentBuilder namedComponents = XContentFactory.jsonBuilder(outputStream)) {
                namedComponents.startObject();
                namedComponents.array("components", componentsClasses.toArray(new String[0]));
                namedComponents.array("extensions_fields", extensionsFields.toArray(new String[0]));
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

    private static List<String> findExtensionsFields(List<ClassReader> classReaders) {
        List<String> extensionFields = new ArrayList<>();

        // TODO: merge with component scanner?
        for (ClassReader classReader : classReaders) {
            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                private String currentClassName;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    currentClassName = pathToClassName(name);
                }

                @Override
                public FieldVisitor visitField(int access, String fieldName, String descriptor, String signature, Object value) {
                    if (Modifier.isStatic(access) == false || Modifier.isFinal(access) == false) {
                        return super.visitField(access, fieldName, descriptor, signature, value);
                    }
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if (annotationDescriptor.equals(Type.getDescriptor(Extension.class))) {
                                // TODO: check that extension type is @Extensible
                                extensionFields.add(currentClassName + "#" + fieldName);
                            }
                            return super.visitAnnotation(annotationDescriptor, visible);
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }

        return extensionFields;

    }

    private static String pathToClassName(String classWithSlashes) {
        return classWithSlashes.replace('/', '.');
    }

}

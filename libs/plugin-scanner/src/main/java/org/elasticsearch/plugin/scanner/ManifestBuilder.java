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
import org.elasticsearch.plugin.RegistryCtor;
import org.elasticsearch.plugin.RegistryEntry;
import org.elasticsearch.plugin.RegistryType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManifestBuilder {

    // main method to be used by gradle build plugin
    public static void main(String[] args) throws IOException {
        List<ClassReader> classReaders = ClassReaders.ofClassPath();

        List<String> components = findComponents(classReaders);
        Map<String, List<EntryInfo>> registries = findRegistries(classReaders);
        Path outputFile = Path.of(args[0]);
        ManifestBuilder.writeToFile(components, registries, outputFile);
    }

    public static void writeToFile(List<String> components, Map<String, List<EntryInfo>> registries, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            try (XContentBuilder namedComponents = XContentFactory.jsonBuilder(outputStream)) {
                namedComponents.startObject();
                namedComponents.array("components", components.toArray(new String[0]));
                namedComponents.startObject("registries");
                for (var entry : registries.entrySet()) {
                    namedComponents.startArray(entry.getKey());
                    for (var value : entry.getValue()) {
                        namedComponents.startObject();
                        namedComponents.field("impl", value.implClazz);
                        namedComponents.field("name", value.name);
                        namedComponents.field("category", value.categoryClazz);
                        namedComponents.field("factoryMethod", value.factoryMethodName);
                        namedComponents.endObject();
                    }
                    namedComponents.endArray();
                }
                namedComponents.endObject();
                namedComponents.endObject();
            }
        }

    }

    public static List<String> findComponents(List<ClassReader> classReaders) {

        ClassScanner componentScanner = new ClassScanner(Type.getDescriptor(Component.class), (path, reader, map) -> {
            String classname = pathToClassName(path);
            map.put(classname, classname);
            return null;
        });
        componentScanner.visit(classReaders);
        // quick hack to reuse ClassScanner
        return componentScanner.getFoundClasses().keySet().stream().sorted().toList();
    }

    public record EntryInfo(String implClazz, String name, String categoryClazz, String factoryMethodName) {}

    static final String registryCtorDescriptor = Type.getDescriptor(RegistryCtor.class);

    public static Map<String, List<EntryInfo>> findRegistries(List<ClassReader> classReaders) {
        ClassScanner registryTypeScanner = new ClassScanner(Type.getDescriptor(RegistryType.class), (path, reader, map) -> {
            map.put(path, pathToClassName(path));
            return null;
        });
        registryTypeScanner.visit(classReaders);

        Map<String, List<EntryInfo>> registries = new HashMap<>();
        ClassScanner registryEntryScanner = new ClassScanner(
            Type.getDescriptor(RegistryEntry.class),
            (classname, reader, map) -> new AnnotationVisitor(Opcodes.ASM9) {
                String name;
                Type categoryClazz;
                String factoryMethodName;
                Type registryType;

                @Override
                public void visit(String key, Object value) {
                    if (key.equals("name")) {
                        assert value instanceof String;
                        name = (String) value;
                    } else if (key.equals("category")) {
                        assert value instanceof Type;
                        categoryClazz = (Type) value;
                    } else if (key.equals("type")) {
                        assert value instanceof Type;
                        registryType = (Type) value;
                    } else {
                        throw new AssertionError("unexpected RegistryEntry key");
                    }
                }

                @Override
                public void visitEnd() {
                    var methodVisitor = new ClassVisitor(Opcodes.ASM9) {
                        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                    if (descriptor.equals(registryCtorDescriptor)) {
                                        factoryMethodName = name;
                                    }
                                    return null;
                                }
                            };
                        }
                    };
                    reader.accept(methodVisitor, 0);

                    String superRegistryType = registryTypeScanner.getFoundClasses().get(classname);
                    if (superRegistryType == null) {
                        throw new RuntimeException("Class [" + classname + "] does not extend registry type");
                    }
                    registries.computeIfAbsent(registryType.getClassName(), k -> new ArrayList<>())
                        .add(new EntryInfo(pathToClassName(classname), name, categoryClazz.getClassName(), factoryMethodName));
                }
            }
        );
        registryEntryScanner.visit(classReaders);

        return registries;
    }

    private static String pathToClassName(String classWithSlashes) {
        return classWithSlashes.replace('/', '.');
    }

}

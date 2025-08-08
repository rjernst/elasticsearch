/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugin.scanner;

import org.elasticsearch.asm.DelegatingClassVisitor;
import org.elasticsearch.asm.HierarchyClassVisitor;
import org.elasticsearch.asm.StatefulClassVisitor;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StablePluginNamedComponentsCollector extends DelegatingClassVisitor implements StatefulClassVisitor {

    private class ExtensionClassVisitor extends ClassVisitor {
        private static final String DESCRIPTOR = "org/elasticsearch/plugin/Extension";

        final Set<String> extensionClasses = new HashSet<>();

        private ExtensionClassVisitor(int api) {
            super(api);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(DESCRIPTOR)) {
                extensionClasses.add(currentClassName);
            }
            return null;
        }
    }

    private class NamedComponentClassVisitor extends ClassVisitor {

        private static final String DESCRIPTOR = "org/elasticsearch/plugin/NamedComponent";

        private Map<String, String> namedComponents = new HashMap<>();

        protected NamedComponentClassVisitor(int api) {
            super(api);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(DESCRIPTOR)) {
                return new AnnotationVisitor(api) {
                    @Override
                    public void visit(String key, Object value) {
                        assert key.equals("value");
                        assert value instanceof String;
                        namedComponents.put(value.toString(), currentClassName);
                    }
                };
            }
            return null;
        }
    }

    private final HierarchyClassVisitor hierarchyClassVisitor;
    private final ExtensionClassVisitor extensionCollector;
    private final NamedComponentClassVisitor namedComponentCollector;
    private final List<ClassVisitor> delegates;
    private String currentClassName = null;

    protected StablePluginNamedComponentsCollector(int api) {
        super(api, null);
        this.hierarchyClassVisitor = new HierarchyClassVisitor(api);
        this.extensionCollector = new ExtensionClassVisitor(api);
        this.namedComponentCollector = new NamedComponentClassVisitor(api);
        this.delegates = List.of(hierarchyClassVisitor, extensionCollector, namedComponentCollector);
    }

    @Override
    public List<ClassVisitor> getDelegates() {
        return delegates;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentClassName = name.replace('/', '.');
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private Map<String, Map<String, String>> getNamedComponentInfo() {
        Map<String, Map<String, String>> componentInfo = new HashMap<>();

        // expand extension classes with hierarchy
        Map<String, Set<String>> classToExtensions = hierarchyClassVisitor.getDescendants(extensionCollector.extensionClasses);

        for (var e : namedComponentCollector.namedComponents.entrySet()) {
            String name = e.getKey();
            String classnameWithSlashes = e.getValue();
            Set<String> extensions = classToExtensions.get(classnameWithSlashes);
            if (extensions == null) {
                throw new RuntimeException(
                    "Named component " + name + "(" + pathToClassName(classnameWithSlashes) + ") does not extend from an extensible class"
                );
            }
            for (String extension : extensions) {
                var named = componentInfo.computeIfAbsent(pathToClassName(extension), k -> new HashMap<>());
                named.put(name, pathToClassName(classnameWithSlashes));
            }
        }
        return componentInfo;
    }

    private static String pathToClassName(String classname) {
        return classname.replace('/', '.');
    }

    @Override
    public void writeState(Path file) throws IOException {
        Files.createDirectories(file.getParent());

        try (OutputStream outputStream = Files.newOutputStream(file)) {
            try (XContentBuilder namedComponents = XContentFactory.jsonBuilder(outputStream)) {
                namedComponents.startObject();
                for (Map.Entry<String, Map<String, String>> extensibleToComponents : getNamedComponentInfo().entrySet()) {
                    namedComponents.startObject(extensibleToComponents.getKey());// extensible class name
                    for (Map.Entry<String, String> components : extensibleToComponents.getValue().entrySet()) {
                        namedComponents.field(components.getKey(), components.getValue());// component name : component class
                    }
                    namedComponents.endObject();
                }
                namedComponents.endObject();
            }
        }
    }
}

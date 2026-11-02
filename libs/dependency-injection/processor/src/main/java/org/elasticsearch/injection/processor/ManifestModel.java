/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the processor found in one compilation, and its serialization to the manifest JSON. Serialization is
 * deterministic: everything is sorted by class name, then registry key, then entry name.
 */
final class ManifestModel {

    /** Bumped when the manifest format changes incompatibly; readers reject versions they do not know. */
    static final int VERSION = 1;

    /** A {@code @Component} class, with the binary names of its injection constructor parameters and interfaces. */
    record ComponentModel(String className, List<String> constructorParameterTypes, List<String> interfaces) {}

    /**
     * An entry of a registry. {@code factory} is null when the registry has no factory marker. Attribute
     * values are strings, numbers, booleans or lists of those.
     */
    record RegistryEntryModel(String key, String className, String factory, Map<String, Object> attributes) {

        String category() {
            return attributeAsString("category");
        }

        String name() {
            return attributeAsString("name");
        }

        private String attributeAsString(String attribute) {
            Object value = attributes.get(attribute);
            return value == null ? "" : value.toString();
        }
    }

    private static final Comparator<ComponentModel> COMPONENT_ORDER = Comparator.comparing(ComponentModel::className);

    private static final Comparator<RegistryEntryModel> ENTRY_ORDER = Comparator.comparing(RegistryEntryModel::className)
        .thenComparing(RegistryEntryModel::key)
        .thenComparing(RegistryEntryModel::name)
        .thenComparing(RegistryEntryModel::category);

    private final List<ComponentModel> components = new ArrayList<>();
    private final List<RegistryEntryModel> entries = new ArrayList<>();

    void add(ComponentModel component) {
        components.add(component);
    }

    void add(RegistryEntryModel entry) {
        entries.add(entry);
    }

    boolean isEmpty() {
        return components.isEmpty() && entries.isEmpty();
    }

    String toJson() {
        var json = new JsonWriter();
        json.raw("{\n  \"version\": " + VERSION + ",\n  \"components\": [");
        List<ComponentModel> sortedComponents = components.stream().sorted(COMPONENT_ORDER).toList();
        for (int i = 0; i < sortedComponents.size(); i++) {
            ComponentModel c = sortedComponents.get(i);
            json.raw(i == 0 ? "\n    " : ",\n    ");
            json.raw("{\"class\": ").value(c.className());
            json.raw(", \"constructor\": ").value(c.constructorParameterTypes());
            json.raw(", \"interfaces\": ").value(c.interfaces()).raw("}");
        }
        json.raw(sortedComponents.isEmpty() ? "],\n" : "\n  ],\n");

        Map<String, List<RegistryEntryModel>> byKey = new TreeMap<>();
        for (RegistryEntryModel entry : entries.stream().sorted(ENTRY_ORDER).toList()) {
            byKey.computeIfAbsent(entry.key(), k -> new ArrayList<>()).add(entry);
        }
        json.raw("  \"registries\": {");
        boolean firstKey = true;
        for (var registry : byKey.entrySet()) {
            json.raw(firstKey ? "\n    " : ",\n    ");
            firstKey = false;
            json.value(registry.getKey()).raw(": [");
            boolean firstEntry = true;
            for (RegistryEntryModel entry : registry.getValue()) {
                json.raw(firstEntry ? "\n      " : ",\n      ");
                firstEntry = false;
                json.raw("{\"class\": ").value(entry.className());
                if (entry.factory() != null) {
                    json.raw(", \"factory\": ").value(entry.factory());
                }
                json.raw(", \"attributes\": ").value(entry.attributes()).raw("}");
            }
            json.raw("\n    ]");
        }
        json.raw(byKey.isEmpty() ? "}\n}\n" : "\n  }\n}\n");
        return json.toString();
    }
}

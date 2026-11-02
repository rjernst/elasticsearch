/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugins;

import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The build-time dependency injection index of a single jar (or of everything visible to a class loader), as written by the
 * {@code DependencyInjectionProcessor} annotation processor. Reading the index at startup lets the node discover components and
 * registry entries without scanning class files.
 *
 * @param components the {@code @Component} classes, with the information needed to construct them
 * @param registries registry entry declarations (named writeables, named xcontent, persistent tasks, ...) keyed by registry type
 */
public record DependencyInjectionManifest(List<ComponentInfo> components, Map<String, List<RegistryEntryInfo>> registries) {

    /**
     * A discovered component.
     *
     * @param className the binary name of the component class
     * @param constructorParameterTypes the binary names of the erased parameter types of the constructor to use, in order
     * @param interfaces the binary names of every interface the class implements, directly or through a supertype
     */
    public record ComponentInfo(String className, List<String> constructorParameterTypes, List<String> interfaces) {
        public ComponentInfo {
            constructorParameterTypes = List.copyOf(constructorParameterTypes);
            interfaces = List.copyOf(interfaces);
        }
    }

    /**
     * A discovered registry entry.
     *
     * @param className the binary name of the annotated class
     * @param factoryMethod the name of the constructor ({@code "<init>"}) or static method that creates the entry, or {@code null} if
     *                      the registry type has no factory
     * @param attributes the elements of the declaring annotation; values are strings, numbers, booleans or lists of those
     */
    public record RegistryEntryInfo(String className, @Nullable String factoryMethod, Map<String, Object> attributes) {
        public RegistryEntryInfo {
            // keep the manifest's attribute order, unlike Map.copyOf
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }

    /** The name of the manifest file at the root of a plugin or module bundle directory. */
    public static final String FILE_NAME = "dependency-injection.json";

    /** The location of the manifest within a jar, used for the server's own manifest. */
    public static final String RESOURCE_PATH = "META-INF/elasticsearch/" + FILE_NAME;

    /** The only manifest format version this class can read. */
    private static final int SUPPORTED_VERSION = 1;

    public static final DependencyInjectionManifest EMPTY = new DependencyInjectionManifest(List.of(), Map.of());

    public DependencyInjectionManifest {
        components = List.copyOf(components);
        // keep the manifest's registry order, unlike Map.copyOf
        Map<String, List<RegistryEntryInfo>> copy = new LinkedHashMap<>();
        registries.forEach((key, entries) -> copy.put(key, List.copyOf(entries)));
        registries = Collections.unmodifiableMap(copy);
    }

    /**
     * Parses a single manifest.
     */
    public static DependencyInjectionManifest load(InputStream json) throws IOException {
        return load(json, "<stream>");
    }

    /**
     * Reads the manifest from the root of a plugin or module bundle directory, or returns {@link #EMPTY} if the bundle has none.
     */
    public static DependencyInjectionManifest load(Path bundleDir) {
        Path file = bundleDir.resolve(FILE_NAME);
        if (Files.exists(file) == false) {
            return EMPTY;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return load(in, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dependency injection manifest [" + file + "]", e);
        }
    }

    /**
     * Reads the manifest the build embedded in the jar that contains {@code owner}, or returns {@link #EMPTY} if it has none. This is how
     * the server reads its own manifest, since the server is not a bundle. The server is a named module, so only its own jar is consulted.
     */
    public static DependencyInjectionManifest loadFromResource(Class<?> owner) {
        try (InputStream in = owner.getResourceAsStream("/" + RESOURCE_PATH)) {
            return in == null ? EMPTY : load(in, owner.getName() + " resource [" + RESOURCE_PATH + "]");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dependency injection manifest of [" + owner.getName() + "]", e);
        }
    }

    /**
     * Returns a manifest containing the entries of this manifest followed by those of {@code other}.
     */
    public DependencyInjectionManifest merge(DependencyInjectionManifest other) {
        if (other.components.isEmpty() && other.registries.isEmpty()) {
            return this;
        }
        if (components.isEmpty() && registries.isEmpty()) {
            return other;
        }
        List<ComponentInfo> mergedComponents = new ArrayList<>(components);
        mergedComponents.addAll(other.components);
        Map<String, List<RegistryEntryInfo>> mergedRegistries = new LinkedHashMap<>();
        registries.forEach((key, entries) -> mergedRegistries.put(key, new ArrayList<>(entries)));
        other.registries.forEach((key, entries) -> mergedRegistries.computeIfAbsent(key, k -> new ArrayList<>()).addAll(entries));
        return new DependencyInjectionManifest(mergedComponents, mergedRegistries);
    }

    private static DependencyInjectionManifest load(InputStream json, String source) throws IOException {
        Map<String, Object> root;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY, json)) {
            root = parser.map();
        }
        Object version = root.get("version");
        if ((version instanceof Integer v && v == SUPPORTED_VERSION) == false) {
            throw new IllegalStateException(
                "Unsupported dependency injection manifest version [" + version + "] in [" + source + "], expected [1]"
            );
        }

        List<ComponentInfo> components = new ArrayList<>();
        for (Map<String, Object> component : objectList(root.get("components"), "components", source)) {
            components.add(
                new ComponentInfo(
                    string(component, "class", source),
                    stringList(component.get("constructor"), "constructor", source),
                    stringList(component.get("interfaces"), "interfaces", source)
                )
            );
        }

        Map<String, List<RegistryEntryInfo>> registries = new LinkedHashMap<>();
        Object registriesValue = root.get("registries");
        if (registriesValue != null) {
            for (Map.Entry<String, Object> registry : asMap(registriesValue, "registries", source).entrySet()) {
                List<RegistryEntryInfo> entries = new ArrayList<>();
                for (Map<String, Object> entry : objectList(registry.getValue(), registry.getKey(), source)) {
                    Object factory = entry.get("factory");
                    Object attributes = entry.get("attributes");
                    entries.add(
                        new RegistryEntryInfo(
                            string(entry, "class", source),
                            factory == null ? null : string(entry, "factory", source),
                            attributes == null ? Map.of() : asMap(attributes, "attributes", source)
                        )
                    );
                }
                registries.put(registry.getKey(), entries);
            }
        }
        return new DependencyInjectionManifest(components, registries);
    }

    private static String string(Map<String, Object> object, String field, String source) {
        if (object.get(field) instanceof String s) {
            return s;
        }
        throw malformed("[" + field + "] must be a string", source);
    }

    private static List<String> stringList(@Nullable Object value, String field, String source) {
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object element : asList(value, field, source)) {
            if (element instanceof String s) {
                result.add(s);
            } else {
                throw malformed("[" + field + "] must be an array of strings", source);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> objectList(@Nullable Object value, String field, String source) {
        if (value == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : asList(value, field, source)) {
            result.add(asMap(element, field, source));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String field, String source) {
        // the parser only ever produces String keys for maps
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw malformed("[" + field + "] must be an object", source);
    }

    private static List<?> asList(Object value, String field, String source) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw malformed("[" + field + "] must be an array", source);
    }

    private static IllegalStateException malformed(String message, String source) {
        return new IllegalStateException("Malformed dependency injection manifest in [" + source + "]: " + message);
    }
}

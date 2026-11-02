/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import org.elasticsearch.injection.processor.ManifestModel.ComponentModel;
import org.elasticsearch.injection.processor.ManifestModel.RegistryEntryModel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Collects the dependency injection participants of a compilation into
 * {@code META-INF/elasticsearch/dependency-injection.json}.
 * <p>
 * Two kinds of participants are recognized. Classes annotated with {@code @Component} are recorded with the
 * constructor that will be used to inject them. Classes annotated with an annotation that is itself annotated
 * with {@code @RegistryEntryType} (or with a repeatable container of such an annotation) are recorded as
 * entries of the registry named by that meta-annotation. Because registries define their own annotations, the
 * processor needs no knowledge of any of them; and because annotations are matched by qualified name, the
 * processor has no dependency on the annotation library either.
 * <p>
 * The compiler already indexes annotations, so this is a single pass over the annotated elements; no class
 * files are scanned.
 */
public final class DependencyInjectionProcessor extends AbstractProcessor {

    static final String MANIFEST_PATH = "META-INF/elasticsearch/dependency-injection.json";

    private static final String API_PACKAGE = "org.elasticsearch.injection.api.";
    private static final String COMPONENT = API_PACKAGE + "Component";
    private static final String INJECT = API_PACKAGE + "Inject";
    private static final String REGISTRY_ENTRY_TYPE = API_PACKAGE + "RegistryEntryType";
    private static final String NO_MARKER_BINARY_NAME = REGISTRY_ENTRY_TYPE + "$NoMarker";

    /** How a registry entry annotation was declared: the registry it belongs to and how instances are created. */
    private record RegistryType(String key, String factoryMarker) {}

    private final ManifestModel manifest = new ManifestModel();
    private final Set<String> seenEntries = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().contentEquals(COMPONENT)) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    handleComponent(element);
                }
                continue;
            }
            RegistryType registryType = registryType(annotation);
            if (registryType != null) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    for (AnnotationMirror mirror : mirrorsOf(element, annotation)) {
                        handleEntry(element, mirror, registryType);
                    }
                }
                continue;
            }
            TypeElement entryAnnotation = repeatedEntryAnnotation(annotation);
            if (entryAnnotation != null) {
                RegistryType containedType = registryType(entryAnnotation);
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    for (AnnotationMirror container : mirrorsOf(element, annotation)) {
                        for (AnnotationValue contained : valuesOf(container)) {
                            handleEntry(element, (AnnotationMirror) contained.getValue(), containedType);
                        }
                    }
                }
            }
        }

        if (roundEnv.processingOver() && roundEnv.errorRaised() == false && manifest.isEmpty() == false) {
            writeManifest();
        }
        return false;
    }

    private void writeManifest() {
        try {
            var resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", MANIFEST_PATH);
            try (OutputStream out = resource.openOutputStream()) {
                out.write(manifest.toJson().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not write " + MANIFEST_PATH + ": " + e);
        }
    }

    // ---- annotation classification

    /** Returns the registry declared by a {@code @RegistryEntryType} meta-annotation on {@code annotation}, or null. */
    private RegistryType registryType(TypeElement annotation) {
        for (AnnotationMirror mirror : annotation.getAnnotationMirrors()) {
            if (isType(mirror, REGISTRY_ENTRY_TYPE) == false) {
                continue;
            }
            String key = null;
            String factoryMarker = null;
            for (var value : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                String name = value.getKey().getSimpleName().toString();
                if (name.equals("key")) {
                    key = (String) value.getValue().getValue();
                } else if (name.equals("factoryMarker")) {
                    String markerName = binaryName((TypeMirror) value.getValue().getValue());
                    factoryMarker = markerName.equals(NO_MARKER_BINARY_NAME) ? null : markerName;
                }
            }
            return new RegistryType(key, factoryMarker);
        }
        return null;
    }

    /**
     * If {@code annotation} is a repeatable container, that is its only element is {@code value()} returning an
     * array of a registry entry annotation, returns that contained annotation; otherwise null.
     */
    private TypeElement repeatedEntryAnnotation(TypeElement annotation) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(annotation.getEnclosedElements());
        if (methods.size() != 1 || methods.get(0).getSimpleName().contentEquals("value") == false) {
            return null;
        }
        if (methods.get(0).getReturnType() instanceof ArrayType array
            && array.getComponentType() instanceof DeclaredType contained
            && contained.asElement() instanceof TypeElement containedAnnotation
            && containedAnnotation.getKind() == ElementKind.ANNOTATION_TYPE
            && registryType(containedAnnotation) != null) {
            return containedAnnotation;
        }
        return null;
    }

    private static List<? extends AnnotationMirror> mirrorsOf(Element element, TypeElement annotation) {
        return element.getAnnotationMirrors().stream().filter(m -> m.getAnnotationType().asElement().equals(annotation)).toList();
    }

    /** The elements of the {@code value()} array of a repeatable container. */
    private static List<? extends AnnotationValue> valuesOf(AnnotationMirror container) {
        for (var value : container.getElementValues().entrySet()) {
            if (value.getKey().getSimpleName().contentEquals("value")) {
                @SuppressWarnings("unchecked") // value() of a repeatable container is always an array, which is modeled as a list
                var values = (List<? extends AnnotationValue>) value.getValue().getValue();
                return values;
            }
        }
        return List.of();
    }

    private static boolean isType(AnnotationMirror mirror, String qualifiedName) {
        return mirror.getAnnotationType().asElement() instanceof TypeElement type && type.getQualifiedName().contentEquals(qualifiedName);
    }

    // ---- components

    private void handleComponent(Element element) {
        if ((element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD)
            || element.getModifiers().contains(Modifier.ABSTRACT)) {
            error(element, "@Component can only be applied to a non-abstract class, but [" + element + "] is not");
            return;
        }
        TypeElement type = (TypeElement) element;
        try {
            ExecutableElement constructor = selectConstructor(type);
            if (constructor == null) {
                return;
            }
            List<String> parameterTypes = new ArrayList<>();
            boolean valid = true;
            for (VariableElement parameter : constructor.getParameters()) {
                TypeMirror parameterType = parameter.asType();
                if (parameterType instanceof DeclaredType declared && declared.getTypeArguments().isEmpty() == false) {
                    error(parameter, "parameterized type " + parameterType + " is not injectable; declare a named interface instead");
                    valid = false;
                } else {
                    parameterTypes.add(binaryName(parameterType));
                }
            }
            if (valid) {
                manifest.add(new ComponentModel(binaryName(type.asType()), parameterTypes, interfacesOf(type)));
            }
        } catch (IllegalArgumentException e) {
            error(element, e.getMessage());
        }
    }

    /**
     * Mirrors the constructor choice made at runtime by the injector: the only public constructor, or else the
     * only public constructor annotated with {@code @Inject}. Reports an error and returns null if there is none.
     */
    private ExecutableElement selectConstructor(TypeElement type) {
        List<ExecutableElement> publicConstructors = ElementFilter.constructorsIn(type.getEnclosedElements())
            .stream()
            .filter(c -> c.getModifiers().contains(Modifier.PUBLIC))
            .toList();
        if (publicConstructors.size() == 1) {
            return publicConstructors.get(0);
        }
        List<ExecutableElement> injectConstructors = publicConstructors.stream()
            .filter(c -> c.getAnnotationMirrors().stream().anyMatch(m -> isType(m, INJECT)))
            .toList();
        if (injectConstructors.size() == 1) {
            return injectConstructors.get(0);
        }
        error(
            type,
            "component ["
                + type.getQualifiedName()
                + "] must have exactly one public constructor, or exactly one public constructor annotated with @Inject;"
                + " public constructors: "
                + (publicConstructors.isEmpty() ? "none" : publicConstructors.toString())
        );
        return null;
    }

    /** The binary names of every interface implemented by the class or any of its supertypes, sorted. */
    private List<String> interfacesOf(TypeElement type) {
        Set<String> interfaces = new LinkedHashSet<>();
        collectInterfaces(type.asType(), interfaces, new HashSet<>());
        return interfaces.stream().sorted().toList();
    }

    private void collectInterfaces(TypeMirror type, Set<String> interfaces, Set<String> visited) {
        for (TypeMirror supertype : processingEnv.getTypeUtils().directSupertypes(type)) {
            String name = binaryName(supertype);
            if (visited.add(name) == false) {
                continue;
            }
            if (supertype instanceof DeclaredType declared && declared.asElement().getKind() == ElementKind.INTERFACE) {
                interfaces.add(name);
            }
            collectInterfaces(supertype, interfaces, visited);
        }
    }

    // ---- registry entries

    private void handleEntry(Element element, AnnotationMirror mirror, RegistryType registryType) {
        if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD) {
            error(element, "[" + mirror.getAnnotationType() + "] can only be applied to a class, but [" + element + "] is not");
            return;
        }
        TypeElement type = (TypeElement) element;
        try {
            Map<String, Object> attributes = new LinkedHashMap<>();
            var valuesWithDefaults = processingEnv.getElementUtils().getElementValuesWithDefaults(mirror);
            // iterate the declared elements so attributes come out in declaration order
            for (ExecutableElement declared : ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements())) {
                AnnotationValue value = valuesWithDefaults.get(declared);
                if (value != null) {
                    attributes.put(declared.getSimpleName().toString(), attributeValue(value.getValue()));
                }
            }

            if (checkCategory(type, mirror, valuesWithDefaults) == false) {
                return;
            }

            String factory = null;
            if (registryType.factoryMarker() != null) {
                factory = factoryOf(type, registryType.factoryMarker());
                if (factory == null) {
                    return;
                }
            }

            var entry = new RegistryEntryModel(registryType.key(), binaryName(type.asType()), factory, attributes);
            String id = registryType.key() + "/" + entry.category() + "/" + entry.name();
            if (seenEntries.add(id) == false) {
                error(
                    element,
                    "duplicate [" + registryType.key() + "] entry with category [" + entry.category() + "] and name [" + entry.name() + "]"
                );
                return;
            }
            manifest.add(entry);
        } catch (IllegalArgumentException e) {
            error(element, e.getMessage());
        }
    }

    /** If the annotation has a {@code Class} element named {@code category}, the annotated class must be a subtype of it. */
    private boolean checkCategory(
        TypeElement type,
        AnnotationMirror mirror,
        Map<? extends ExecutableElement, ? extends AnnotationValue> values
    ) {
        for (var value : values.entrySet()) {
            if (value.getKey().getSimpleName().contentEquals("category") && value.getValue().getValue() instanceof TypeMirror category) {
                var types = processingEnv.getTypeUtils();
                if (types.isSubtype(types.erasure(type.asType()), types.erasure(category)) == false) {
                    error(type, "[" + type.getQualifiedName() + "] must be a subtype of its category [" + category + "]");
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Finds the single constructor or static method of {@code type} carrying the factory marker annotation and
     * returns its name ({@code <init>} for a constructor), or reports an error and returns null.
     */
    private String factoryOf(TypeElement type, String markerName) {
        List<ExecutableElement> marked = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if ((member.getKind() == ElementKind.CONSTRUCTOR || member.getKind() == ElementKind.METHOD)
                && member.getAnnotationMirrors().stream().anyMatch(m -> isType(m, markerName))) {
                marked.add((ExecutableElement) member);
            }
        }
        if (marked.size() != 1) {
            error(
                type,
                "["
                    + type.getQualifiedName()
                    + "] must have exactly one constructor or static method annotated with @"
                    + markerName
                    + ", found "
                    + marked.size()
            );
            return null;
        }
        ExecutableElement factory = marked.get(0);
        if (factory.getParameters().size() != 1) {
            error(factory, "@" + markerName + " member [" + factory + "] must have exactly one parameter");
            return null;
        }
        if (factory.getKind() == ElementKind.CONSTRUCTOR) {
            return "<init>";
        }
        var types = processingEnv.getTypeUtils();
        if (factory.getModifiers().contains(Modifier.STATIC) == false) {
            error(factory, "@" + markerName + " method [" + factory + "] must be static");
            return null;
        }
        if (types.isSubtype(types.erasure(type.asType()), types.erasure(factory.getReturnType())) == false) {
            error(
                factory,
                "@" + markerName + " method [" + factory + "] must return [" + type.getQualifiedName() + "] or a supertype of it"
            );
            return null;
        }
        return factory.getSimpleName().toString();
    }

    /**
     * Converts an annotation element value to its manifest form: strings, booleans and numbers as is,
     * {@code Class} values as binary names, enum constants as their names, and arrays as lists of those.
     */
    private Object attributeValue(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        } else if (value instanceof Character c) {
            return c.toString();
        } else if (value instanceof TypeMirror type) {
            return binaryName(type);
        } else if (value instanceof VariableElement enumConstant) {
            return enumConstant.getSimpleName().toString();
        } else if (value instanceof List<?> values) {
            List<Object> converted = new ArrayList<>();
            for (Object v : values) {
                // the elements of an annotation array value are always AnnotationValue
                converted.add(attributeValue(((AnnotationValue) v).getValue()));
            }
            return converted;
        }
        throw new IllegalArgumentException("annotation element value [" + value + "] is not supported in a registry entry");
    }

    // ---- helpers

    /** The binary name of the erasure of {@code type}; primitives by name and arrays with a {@code []} suffix. */
    private String binaryName(TypeMirror type) {
        var types = processingEnv.getTypeUtils();
        TypeMirror erased = types.erasure(type);
        if (erased.getKind().isPrimitive()) {
            return erased.toString();
        }
        return switch (erased.getKind()) {
            case DECLARED -> processingEnv.getElementUtils().getBinaryName((TypeElement) ((DeclaredType) erased).asElement()).toString();
            case ARRAY -> binaryName(((ArrayType) erased).getComponentType()) + "[]";
            case VOID -> "void";
            default -> throw new IllegalArgumentException("unsupported type [" + type + "]");
        };
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}

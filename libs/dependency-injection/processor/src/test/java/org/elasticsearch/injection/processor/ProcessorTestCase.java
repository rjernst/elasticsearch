/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Runs {@link DependencyInjectionProcessor} in-process over test supplied sources, the same way the build does
 * ({@code -proc:only}), and exposes the diagnostics and the manifest it wrote.
 */
abstract class ProcessorTestCase extends TestCase {

    private final List<Path> outputDirs = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        for (Path dir : outputDirs) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        super.tearDown();
    }

    /** The outcome of one compilation: whether it succeeded, its error messages, and the class output directory. */
    record CompilationResult(boolean success, List<String> errors, Path outputDir) {

        /** The manifest text, or null if the processor wrote none. */
        String manifest() throws IOException {
            Path manifest = outputDir.resolve(DependencyInjectionProcessor.MANIFEST_PATH);
            return Files.exists(manifest) ? Files.readString(manifest) : null;
        }
    }

    /** Compiles a single source file. */
    protected CompilationResult compile(String className, String source) {
        return compile(Map.of(className, source));
    }

    /** Compiles the given sources (class name to source text) in one compilation, in map iteration order. */
    protected CompilationResult compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("System Java compiler not available", compiler);

        List<JavaFileObject> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(sources).entrySet()) {
            String source = entry.getValue();
            sourceFiles.add(
                new SimpleJavaFileObject(
                    URI.create("string:///" + entry.getKey().replace('.', '/') + ".java"),
                    JavaFileObject.Kind.SOURCE
                ) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source;
                    }
                }
            );
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try {
            Path outputDir = Files.createTempDirectory(
                Path.of(System.getProperty("java.io.tmpdir")),
                "dependency-injection-processor-test"
            );
            outputDirs.add(outputDir);
            try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
                fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDir));
                // the test classpath has the annotations from :libs:dependency-injection and the sample annotations
                List<String> options = List.of(
                    "-proc:only",
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    DependencyInjectionProcessor.class.getName()
                );
                boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, sourceFiles).call();

                List<String> errors = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        errors.add(d.getMessage(null));
                    }
                }
                return new CompilationResult(success, errors, outputDir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Compilation setup failed", e);
        }
    }

    protected static void assertErrorContaining(CompilationResult result, String expected) {
        assertFalse("Expected compilation to fail", result.success());
        assertTrue(
            "Expected an error containing [" + expected + "] but got: " + result.errors(),
            result.errors().toString().contains(expected)
        );
    }
}

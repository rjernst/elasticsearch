/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencyinjection

import org.elasticsearch.gradle.fixtures.AbstractGradleInternalPluginFuncTest
import org.gradle.testkit.runner.TaskOutcome

import java.util.zip.ZipFile

/**
 * Exercises the plugin's wiring in a real Gradle invocation. Validation of the actual processor's
 * output lives with the processor project itself; here we only verify that the plugin runs an
 * annotation processor against the consumer's sources and that the resource it writes ends up in the jar.
 * A dummy processor is supplied via the {@code dependencyInjectionProcessor} configuration so the test does
 * not need the real {@code :libs:dependency-injection} artifacts; it writes a manifest naming each class
 * annotated with {@code org.elasticsearch.injection.api.Component}, which is stubbed in the sample project.
 */
class ElasticsearchDependencyInjectionPluginFuncTest extends AbstractGradleInternalPluginFuncTest {

    Class<? extends org.gradle.api.Plugin> pluginClassUnderTest = ElasticsearchDependencyInjectionPlugin

    def setup() {
        configureBwcVersions()

        settingsFile << """
            include ':fakeprocessor'
        """.stripIndent()

        file('fakeprocessor/build.gradle') << """
            apply plugin: 'java-library'
        """.stripIndent()

        // Stand-in for the real annotation, which the func test project cannot resolve.
        file('fakeprocessor/src/main/java/org/elasticsearch/injection/api/Component.java') << """
            package org.elasticsearch.injection.api;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface Component {}
        """.stripIndent()

        file('fakeprocessor/src/main/java/fake/ManifestProcessor.java') << """
            package fake;
            import java.io.Writer;
            import java.util.Set;
            import javax.annotation.processing.*;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.Element;
            import javax.lang.model.element.TypeElement;
            import javax.tools.StandardLocation;

            @SupportedAnnotationTypes("org.elasticsearch.injection.api.Component")
            public class ManifestProcessor extends AbstractProcessor {
                private final StringBuilder components = new StringBuilder();

                @Override
                public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
                    for (TypeElement annotation : annotations) {
                        for (Element element : env.getElementsAnnotatedWith(annotation)) {
                            components.append("{\\"class\\": \\"").append(((TypeElement) element).getQualifiedName()).append("\\"}");
                        }
                    }
                    if (env.processingOver() && components.length() > 0) {
                        try {
                            var resource = processingEnv.getFiler().createResource(
                                StandardLocation.CLASS_OUTPUT, "", "META-INF/elasticsearch/dependency-injection.json");
                            try (Writer w = resource.openWriter()) {
                                w.write("{\\"version\\": 1, \\"components\\": [" + components + "]}");
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return true;
                }
            }
        """.stripIndent()

        file('fakeprocessor/src/main/resources/META-INF/services/javax.annotation.processing.Processor') <<
            'fake.ManifestProcessor'

        file('src/main/java/test/Helper.java') << """
            package test;
            public abstract class Helper {}
        """.stripIndent()

        file('src/main/java/test/Service.java') << """
            package test;
            @org.elasticsearch.injection.api.Component
            public class Service extends Helper {}
        """.stripIndent()

        buildFile << """
            apply plugin: 'elasticsearch.dependency-injection'

            // The plugin pins processDependencyInjectionAnnotations to JDK 25 because the real processor is
            // compiled for it. The dummy processor only uses basic javax.annotation.processing APIs, and CI
            // agents only ship the project's minimum runtime, so override the toolchain to the current JVM.
            // The JDK 25 pinning itself is verified by ElasticsearchDependencyInjectionPluginSpec.
            def currentJavaVersion = JavaVersion.current().majorVersion
            tasks.matching { it.name == 'processDependencyInjectionAnnotations' }.configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = org.gradle.jvm.toolchain.JavaLanguageVersion.of(currentJavaVersion.toInteger())
                }
                sourceCompatibility = currentJavaVersion
                targetCompatibility = currentJavaVersion
            }

            // This project has no :libs:dependency-injection:processor, so the plugin falls back to the published
            // processor coordinates, which cannot be resolved here without repositories. Substitute the dummy.
            configurations.dependencyInjectionProcessor.dependencies.clear()

            dependencies {
                compileOnly project(':fakeprocessor')
                dependencyInjectionProcessor project(':fakeprocessor')
            }
        """.stripIndent()
    }

    def "jar contains the manifest written by the processor"() {
        when:
        def result = gradleRunner('jar').build()

        then:
        result.task(":processDependencyInjectionAnnotations").outcome == TaskOutcome.SUCCESS
        file("build/generated-dependency-injection/META-INF/elasticsearch/dependency-injection.json").exists()
        def manifest = manifestFromJar()
        manifest != null
        manifest.contains('"class": "test.Service"')
    }

    private String manifestFromJar() {
        def jarFile = file("build/libs").listFiles().find { it.name.endsWith(".jar") }
        try (ZipFile zip = new ZipFile(jarFile)) {
            def entry = zip.getEntry("META-INF/elasticsearch/dependency-injection.json")
            return entry == null ? null : zip.getInputStream(entry).text
        }
    }

    def "processDependencyInjectionAnnotations reruns after a main source change"() {
        when:
        gradleRunner('processDependencyInjectionAnnotations').build()
        file('src/main/java/test/Service.java').append('\n')
        def result = gradleRunner('processDependencyInjectionAnnotations').build()

        then:
        result.task(":processDependencyInjectionAnnotations").outcome in [TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE]
        file("build/generated-dependency-injection/META-INF/elasticsearch/dependency-injection.json").exists()
    }
}

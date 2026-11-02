/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencyinjection

import org.elasticsearch.gradle.fixtures.AbstractProjectBuilderPluginSpec
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile

class ElasticsearchDependencyInjectionPluginSpec extends AbstractProjectBuilderPluginSpec {

    @Override
    Class<ElasticsearchDependencyInjectionPlugin> getPluginClassUnderTest() {
        return ElasticsearchDependencyInjectionPlugin
    }

    Project consumer

    def setup() {
        def rootProject = buildProject("root")
        def libsProject = buildProject("libs", rootProject)
        def diProject = buildProject("dependency-injection", libsProject)
        def processorProject = buildProject("processor", diProject)
        consumer = buildProject("consumer", rootProject)

        // Apply java-library to the stub processor so it exposes the configurations the consumer resolves through.
        diProject.pluginManager.apply(JavaLibraryPlugin)
        processorProject.pluginManager.apply(JavaLibraryPlugin)

        def sources = new File(consumer.projectDir, "src/main/java")
        new File(sources, "test").mkdirs()
        new File(sources, "module-info.java").text = "module test {}"
        new File(sources, "test/Service.java").text = "package test; public class Service {}"

        applyPluginUnderTest(consumer)
    }

    def "creates processor configuration with the processor project as its dependency"() {
        when:
        def proc = consumer.configurations.getByName(ElasticsearchDependencyInjectionPlugin.PROCESSOR_CONFIGURATION_NAME)
        def dep = proc.dependencies.iterator().next()

        then:
        dep instanceof ProjectDependency
        ((ProjectDependency) dep).path == ":libs:dependency-injection:processor"
    }

    def "registers processDependencyInjectionAnnotations as a JavaCompile with the expected wiring"() {
        when:
        def task = (JavaCompile) consumer.tasks.getByName(ElasticsearchDependencyInjectionPlugin.PROCESS_ANNOTATIONS_TASK_NAME)

        then:
        task.destinationDirectory.get().asFile == new File(consumer.layout.buildDirectory.get().asFile, "generated-dependency-injection")
        "-proc:only" in task.options.compilerArgs
        task.options.annotationProcessorPath == consumer.configurations.getByName(ElasticsearchDependencyInjectionPlugin.PROCESSOR_CONFIGURATION_NAME)
        // The processor is compiled for JDK 25, so the plugin pins the toolchain to JDK 25 and leaves --release unset.
        task.sourceCompatibility == "25"
        task.targetCompatibility == "25"
        task.options.release.isPresent() == false
        // module-info.java is not compiled: the processor works on the classpath only
        task.source.files*.name == ["Service.java"]
    }

    def "processDependencyInjectionAnnotations depends on compileJava and includes its output"() {
        when:
        def processAnnotations = (JavaCompile) consumer.tasks.getByName(ElasticsearchDependencyInjectionPlugin.PROCESS_ANNOTATIONS_TASK_NAME)
        def compile = (JavaCompile) consumer.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
        def deps = processAnnotations.taskDependencies.getDependencies(processAnnotations).collect { it.name }

        then:
        JavaPlugin.COMPILE_JAVA_TASK_NAME in deps
        compile.destinationDirectory.get().asFile in processAnnotations.classpath.files
    }

    def "compileJava is independent of processDependencyInjectionAnnotations"() {
        when:
        def compileJava = (JavaCompile) consumer.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
        def deps = compileJava.taskDependencies.getDependencies(compileJava).collect { it.name }

        then:
        ElasticsearchDependencyInjectionPlugin.PROCESS_ANNOTATIONS_TASK_NAME !in deps
    }

    def "main source set output includes the generated manifest dir"() {
        when:
        def sourceSets = consumer.extensions.getByType(SourceSetContainer)
        def main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        def expected = new File(consumer.layout.buildDirectory.get().asFile, "generated-dependency-injection")

        then:
        expected in main.output.dirs.files
    }

    def "only the main source set is processed"() {
        expect:
        consumer.tasks.withType(JavaCompile).findAll { it.name.startsWith("process") }*.name ==
            [ElasticsearchDependencyInjectionPlugin.PROCESS_ANNOTATIONS_TASK_NAME]
    }
}

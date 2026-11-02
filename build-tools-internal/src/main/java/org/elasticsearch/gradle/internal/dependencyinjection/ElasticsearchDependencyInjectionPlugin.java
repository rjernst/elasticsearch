/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencyinjection;

import org.elasticsearch.gradle.VersionProperties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.Map;

import javax.inject.Inject;

/**
 * Runs the dependency injection annotation processor from {@code libs/dependency-injection/processor}
 * over the main source set, so that the jar carries a {@code META-INF/elasticsearch/dependency-injection.json}
 * manifest describing its components and registry entries.
 *
 * <p>The processor is compiled for Java 25, but {@code ElasticsearchJavaBasePlugin} pins every
 * {@link JavaCompile} to the project's minimum runtime toolchain, so the processor cannot run inside
 * {@code compileJava}. It runs in a separate {@code -proc:only} compilation on a Java 25 toolchain instead,
 * and the directory holding the manifest is added to the main source set output so that the {@code jar} task
 * and test runtime classpaths pick it up; that copy is what the server reads for itself. Plugins and modules
 * additionally ship the manifest at the root of their bundle (see {@link #addManifestToBundle}), because the node
 * sees a bundle as a flat directory of jars and reads the manifest from the directory instead of searching jars.
 * The processor only writes a resource, so no {@code module-info} augmentation or jar swapping is needed.
 */
public class ElasticsearchDependencyInjectionPlugin implements Plugin<Project> {

    private static final JavaLanguageVersion PROCESSOR_TOOLCHAIN = JavaLanguageVersion.of(25);
    private static final String PROCESSOR_PROJECT = ":libs:dependency-injection:processor";
    private static final String PROCESSOR_MODULE = "org.elasticsearch:elasticsearch-dependency-injection-processor:";

    private static final String MODULE_INFO_SOURCE = "module-info.java";

    static final String PROCESSOR_CONFIGURATION_NAME = "dependencyInjectionProcessor";
    static final String PROCESS_ANNOTATIONS_TASK_NAME = "processDependencyInjectionAnnotations";
    static final String GENERATED_DIR = "generated-dependency-injection";

    /** Where the processor writes the manifest, relative to the task's destination directory. */
    private static final String MANIFEST_RESOURCE = "META-INF/elasticsearch/dependency-injection.json";
    /** Where a plugin or module bundle carries the manifest, relative to the bundle root. */
    private static final String BUNDLE_MANIFEST_FILE = "dependency-injection.json";

    private final JavaToolchainService javaToolchains;

    @Inject
    public ElasticsearchDependencyInjectionPlugin(JavaToolchainService javaToolchains) {
        this.javaToolchains = javaToolchains;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        Configuration processorConfiguration = project.getConfigurations().create(PROCESSOR_CONFIGURATION_NAME);
        // Guarded so the plugin tolerates serverless composite builds, where the processor project does not
        // exist locally and is supplied by GAV instead.
        if (project.findProject(PROCESSOR_PROJECT) != null) {
            project.getDependencies()
                .add(PROCESSOR_CONFIGURATION_NAME, project.getDependencies().project(Map.of("path", PROCESSOR_PROJECT)));
        } else {
            project.getDependencies().add(PROCESSOR_CONFIGURATION_NAME, PROCESSOR_MODULE + VersionProperties.getElasticsearch());
        }

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        TaskProvider<JavaCompile> processAnnotations = registerProcessAnnotationsTask(project, mainSourceSet, processorConfiguration);
        mainSourceSet.getOutput().dir(processAnnotations.flatMap(JavaCompile::getDestinationDirectory));
    }

    /**
     * Copies the manifest to the root of a plugin or module bundle, where the node reads it from. The server is not a bundle, so it
     * instead reads the copy embedded in its jar.
     */
    public static void addManifestToBundle(Project project, CopySpec bundleSpec) {
        TaskProvider<JavaCompile> processAnnotations = project.getTasks().named(PROCESS_ANNOTATIONS_TASK_NAME, JavaCompile.class);
        bundleSpec.from(processAnnotations, spec -> {
            spec.include(MANIFEST_RESOURCE);
            spec.eachFile(file -> file.setRelativePath(new RelativePath(true, BUNDLE_MANIFEST_FILE)));
            spec.setIncludeEmptyDirs(false);
        });
    }

    private TaskProvider<JavaCompile> registerProcessAnnotationsTask(
        Project project,
        SourceSet sourceSet,
        Configuration processorConfiguration
    ) {
        TaskProvider<JavaCompile> compileTask = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);

        TaskProvider<JavaCompile> task = project.getTasks().register(PROCESS_ANNOTATIONS_TASK_NAME, JavaCompile.class, t -> {
            // The processor matches annotations by name and has no use for module declarations. Compiling
            // module-info.java would put the compilation in module mode, which needs the module path that
            // ElasticsearchJavaModulePathPlugin only computes for compileJava (e.g. for server, whose
            // requires of non-modular libraries cannot be resolved from the classpath), so leave it out and
            // treat everything as classpath.
            t.setSource(sourceSet.getJava().matching(filter -> filter.exclude(MODULE_INFO_SOURCE)));
            t.setClasspath(sourceSet.getCompileClasspath());
            t.getOptions().setAnnotationProcessorPath(processorConfiguration);
            t.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir(GENERATED_DIR));
            t.getOptions().getCompilerArgs().add("-proc:only");
        });

        // ElasticsearchJavaBasePlugin's withType(JavaCompile) configureEach pins the toolchain to the
        // project's minimum runtime (JDK 21) and sets options.release to it; the processor is compiled for
        // JDK 25 so it must run on that toolchain, and pinning --release would reject sources referencing
        // now-finalized preview APIs. TaskProvider.configure runs after configureEach, so these overrides win.
        task.configure(t -> {
            t.getJavaCompiler().set(javaToolchains.compilerFor(spec -> spec.getLanguageVersion().set(PROCESSOR_TOOLCHAIN)));
            t.getOptions().getRelease().unset();
            t.setSourceCompatibility(PROCESSOR_TOOLCHAIN.toString());
            t.setTargetCompatibility(PROCESSOR_TOOLCHAIN.toString());
            // -proc:only still parses every source file to find annotations. Sibling classes compiled
            // into the source set output are not on compileClasspath, so resolve them first.
            t.dependsOn(compileTask);
            t.setClasspath(sourceSet.getCompileClasspath().plus(sourceSet.getOutput().getClassesDirs()));
        });

        return task;
    }
}

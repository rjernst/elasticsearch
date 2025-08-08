/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.classscanner;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

// runs the actual class loading, calling out to the registered visitors
public abstract class ClassScannerTask extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getClassesDirs();

    @Input
    public List<? extends Class<?>> getClassVisitorClasses() {
        return getClassVisitorTasks().get().stream().map(Object::getClass).toList();
    }

    @Internal
    abstract ListProperty<ClassVisitorTask> getClassVisitorTasks();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private final Project project;
    private final int maxConcurrency;

    @Inject
    public ClassScannerTask(Project project) {
        this.project = project;
        this.maxConcurrency = getProject().getGradle().getStartParameter().getMaxWorkerCount();
    }

    @TaskAction
    public void run() throws Exception {
        // first collect all the files to be scanned
        List<String> paths = new ArrayList<>();
        for (File classesDir : getClassesDirs()) {
            Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    paths.add(file.toAbsolutePath().toString());
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // figure out how many scanners to use

        int numWorkers = paths.size() < 100 ? 1 : maxConcurrency; // TODO: better way to scale this
        getLogger().lifecycle("Scanning " + paths.size() + " classes with " + numWorkers + " workers");

        // setup state output
        List<ClassVisitorTask> visitorTasks = getClassVisitorTasks().get();
        var visitorClasses = visitorTasks.stream().map(ClassVisitorTask::getVisitorClassName).toList();

        Path outputDir = getOutputDirectory().get().getAsFile().toPath();
        project.delete(outputDir);
        for (String classname : visitorClasses) {
            Files.createDirectories(outputDir.resolve(classname));
        }

        Map<String, List<Path>> outputFilesByClass = new HashMap<>();

        WorkQueue workQueue = getWorkerExecutor().processIsolation();
        for (int i = 0; i < numWorkers; ++i) {
            int batchSize = paths.size() / numWorkers;
            int beginNdx = i * batchSize;
            int endNdx = Math.min(beginNdx + batchSize, paths.size());

            List<Path> outputFiles = new ArrayList<>();
            for (int j = 0; j < visitorClasses.size(); ++j) {
                var classname = visitorClasses.get(j);
                Path outputFile = outputDir.resolve(classname).resolve("state-w" + i);
                outputFiles.add(outputFile);
                outputFilesByClass.computeIfAbsent(classname, k -> new ArrayList<>()).add(outputFile);
            }

            workQueue.submit(ClassScannerWorker.class, p -> {
                p.getClassFiles().set(paths.subList(beginNdx, endNdx));
                p.getVisitorClassNames().set(visitorClasses);
                p.getOutputFiles().set(outputFiles.stream().map(Path::toAbsolutePath).map(Path::toString).toList());
            });
        }

        workQueue.await();

        for (ClassVisitorTask task : visitorTasks) {
            task.getVisitorStateFiles().setFrom(outputFilesByClass.get(task.getVisitorClassName()));
        }
    }
}

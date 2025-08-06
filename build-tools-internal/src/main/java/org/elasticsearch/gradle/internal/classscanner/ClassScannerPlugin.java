/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.classscanner;

import org.elasticsearch.gradle.util.GradleUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.SourceSet;

public class ClassScannerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaBasePlugin.class);

        var runnerTask = project.getTasks().register("scanClasses", ClassScannerTask.class, t -> {
            SourceSet mainSourceSet = GradleUtils.getJavaSourceSets(project).findByName(SourceSet.MAIN_SOURCE_SET_NAME);
            t.getClassesDirs().setFrom(mainSourceSet.getOutput().getClassesDirs());
            t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("class-scans"));
        });

        project.getTasks().withType(ClassVisitorTask.class).configureEach(t -> {
            t.dependsOn(runnerTask);
            runnerTask.get().getClassVisitorTasks().add(t);
        });
    }
}

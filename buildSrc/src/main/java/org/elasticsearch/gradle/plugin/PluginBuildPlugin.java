/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.gradle.plugin;

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import nebula.plugin.publishing.maven.MavenPublishPlugin;
import org.elasticsearch.gradle.NoticeTask;
import org.elasticsearch.gradle.Version;
import org.elasticsearch.gradle.VersionProperties;
import org.elasticsearch.gradle.dependencies.CompileOnlyResolvePlugin;
import org.elasticsearch.gradle.info.BuildParams;
import org.elasticsearch.gradle.precommit.TestingConventionRule;
import org.elasticsearch.gradle.test.RestTestBasePlugin;
import org.elasticsearch.gradle.testclusters.ElasticsearchCluster;
import org.elasticsearch.gradle.testclusters.RunTask;
import org.elasticsearch.gradle.testclusters.TestClustersPlugin;
import org.elasticsearch.gradle.util.GradleUtils;
import org.elasticsearch.gradle.util.Util;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Map.entry;

public class PluginBuildPlugin implements Plugin<Project> {
    public static final String PLUGIN_EXTENSION_NAME = "esplugin";
    private Object RestTestBasePlugin;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("elasticsearch.build");
        project.getPluginManager().apply(RestTestBasePlugin.class);
        project.getPluginManager().apply(CompileOnlyResolvePlugin.class);

        PluginPropertiesExtension extension = project.getExtensions().create(PLUGIN_EXTENSION_NAME,
            PluginPropertiesExtension.class, project);
        configureDependencies(project);

        boolean isXPackModule = project.getPath().startsWith(":x-pack:plugin");
        boolean isModule = project.getPath().startsWith(":modules:") || isXPackModule;

        createBundleTasks(project, extension);

        project.afterEvaluate(p -> {
            project.getExtensions().getByType(PluginPropertiesExtension.class).getExtendedPlugins().forEach(pluginName -> {
                // Auto add dependent modules to the test cluster
                String modulePath = ":modules:" + pluginName;
                if (project.findProject(modulePath) != null) {
                    @SuppressWarnings("unchecked")
                    NamedDomainObjectContainer<ElasticsearchCluster> testClusters =
                        (NamedDomainObjectContainer<ElasticsearchCluster>) project.getExtensions()
                            .getByName(TestClustersPlugin.EXTENSION_NAME);
                    testClusters.all(cluster -> cluster.module(modulePath));
                }
            });
            PluginPropertiesExtension extension1 = project.getExtensions().getByType(PluginPropertiesExtension.class);
            if (extension1.getName() == null) {
                throw new InvalidUserDataException("name is a required setting for esplugin");
            }
            if (extension1.getDescription() == null) {
                throw new InvalidUserDataException("description is a required setting for esplugin");
            }
            if (extension1.getType() != PluginType.BOOTSTRAP && extension1.getClassname() == null) {
                throw new InvalidUserDataException("classname is a required setting for esplugin");
            }

            configurePublishing(project, extension1);
            project.getConvention().getPlugin(BasePluginConvention.class).setArchivesBaseName(extension1.getName());
            project.setDescription(extension1.getDescription());

            Map<String, String> properties = Map.ofEntries(
                entry("name",                 extension1.getName()),
                entry("description",          extension1.getDescription()),
                entry("version",              extension1.getVersion()),
                entry("elasticsearchVersion", Version.fromString(VersionProperties.getElasticsearch()).toString()),
                entry("javaVersion",          project.getConvention().getPlugin(JavaPluginConvention.class).getTargetCompatibility().toString()),
                entry("classname",            extension1.getType() == PluginType.BOOTSTRAP ? "" : extension1.getClassname()),
                entry("extendedPlugins",      String.join(",", extension1.getExtendedPlugins())),
                entry("hasNativeController",  Boolean.toString(extension1.isHasNativeController())),
                entry("requiresKeystore",     Boolean.toString(extension1.isRequiresKeystore())),
                entry("type",                 extension1.getType().toString()),
                entry("javaOpts",             extension1.getJavaOpts()));

            project.getTasks().named("pluginProperties", Copy.class).configure(t -> {
                t.expand(properties);
                t.getInputs().properties(properties);
            });
            if (isModule == false || isXPackModule) {
                addNoticeGeneration(project, extension1);
            }
        });

        project.getTasks().named("testingConventions").configure(t -> {
            NamedDomainObjectContainer<TestingConventionRule> naming = getProject().container(TestingConventionRule.class);
            naming.clear();
            naming.register("Tests", rule -> rule.baseClass("org.apache.lucene.util.LuceneTestCase"));
            naming.register("IT", rule -> {
                rule.baseClass("org.elasticsearch.test.ESIntegTestCase");
                rule.baseClass("org.elasticsearch.test.rest.ESRestTestCase");
                rule.baseClass("org.elasticsearch.test.ESSingleNodeTestCase");
            });
        });
        project.getConfigurations().getByName("default")
            .extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
        // allow running ES with this plugin in the foreground of a build
        project.getTasks().register("run", RunTask.class).configure(t ->
            t.dependsOn(project.getTasks().named("bundlePlugin"))
        );
    }

    private static void configurePublishing(Project project, PluginPropertiesExtension extension) {
        if (project.getPlugins().hasPlugin(MavenPublishPlugin.class)) {
            PublishingExtension publishing = project.getExtensions().findByType(PublishingExtension.class);
            Publication publication = publishing.getPublications().getByName("nebula");
            ((MavenPublication) publication).setArtifactId(extension.getName());
        }
    }

    private static void configureDependencies(Project project) {
        DependencyHandler deps = project.getDependencies();
        Map<String, String> versions = VersionProperties.getVersions();
        if (BuildParams.isInternal()) {
            deps.add("compileOnly", project.project(":server"));
            deps.add("testImplementation", project.project(":test:framework"));
        } else {
            String esVersion = VersionProperties.getElasticsearch();
            deps.add("compileOnly", "org.elasticsearch:elasticsearch:" + esVersion);
            deps.add("testImplementation", "org.elasticsearch.test:framework:" + esVersion);
        }
        // we "upgrade" these optional deps to provided for plugins, since they will run
        // with a full elasticsearch server that includes optional deps
        deps.add("compileOnly", "org.locationtech.spatial4j:spatial4j:" + versions.get("spatial4j"));
        deps.add("compileOnly", "org.locationtech.jts:jts-core:" + versions.get("jts"));
        deps.add("compileOnly", "org.apache.logging.log4j:log4j-api:" + versions.get("log4j"));
        deps.add("compileOnly", "org.apache.logging.log4j:log4j-core:" + versions.get("log4j"));
        deps.add("compileOnly", "org.elasticsearch:jna:" + versions.get("jna"));
    }

    /**
     * Adds a bundlePlugin task which builds the zip containing the plugin jars,
     * metadata, properties, and packaging files
     */
    private static void createBundleTasks(Project project, PluginPropertiesExtension extension) {
        File pluginMetadata = project.file("src/main/plugin-metadata");
        File templateFile = new File(project.getBuildDir(), "templates/plugin-descriptor.properties");

        // create tasks to build the properties file for this plugin
        TaskProvider<Task> copyPluginPropertiesTemplate = project.getTasks().register("copyPluginPropertiesTemplate");
        copyPluginPropertiesTemplate.configure(t -> {
            t.getOutputs().file(templateFile);
            t.doLast(task -> {
                InputStream resourceTemplate = PluginBuildPlugin.class.getResourceAsStream("/" + templateFile.getName());
                try {
                    Files.copy(resourceTemplate, templateFile.toPath());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });

        File generatedResources = new File(project.getBuildDir(), "generated-resources");
        TaskProvider<Copy> buildProperties = project.getTasks().register("pluginProperties", Copy.class);
        buildProperties.configure(t -> {
            t.dependsOn(copyPluginPropertiesTemplate);
            t.from(templateFile);
            t.into(generatedResources);
        });

        // add the plugin properties and metadata to test resources, so unit tests can
        // know about the plugin (used by test security code to statically initialize the plugin in unit tests)
        SourceSet testSourceSet = Util.getJavaTestSourceSet(project).get();
        testSourceSet.getOutput().dir(Map.of("builtBy", buildProperties), generatedResources);
        testSourceSet.getResources().srcDir(pluginMetadata);

        // create the actual bundle task, which zips up all the files for the plugin
        TaskProvider<Zip> bundle = project.getTasks().register("bundlePlugin", Zip.class);
        bundle.configure(t -> {
            t.from(buildProperties);
            t.from(pluginMetadata); // metadata (eg custom security policy)
            // If the plugin is using the shadow plugin then we need to bundle that shadow jar.
            t.from((Callable) () -> {
                    final String taskname = project.getPlugins().hasPlugin(ShadowPlugin.class) ? "shadowJar" : "jar";
                    return project.getTasks().named(taskname);
                });

            t.from(project.getConfigurations().getByName("runtimeClasspath").minus(project.getConfigurations().getByName(
                CompileOnlyResolvePlugin.RESOLVEABLE_COMPILE_ONLY_CONFIGURATION_NAME
            )));
            // extra files for the plugin to go into the zip
            t.from("src/main/packaging"); // TODO: move all config/bin/_size/etc into packaging
            t.from("src/main", spec -> {
                spec.include("config/**");
                spec.include("bin/**");
            });
        });
        project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(t -> t.dependsOn(bundle));

        // also make the zip available as a configuration (used when depending on this project)
        project.getConfigurations().create("zip");
        project.getArtifacts().add("zip", bundle);
    }

    /** Configure the pom for the main jar of this plugin */

    protected static void addNoticeGeneration(Project project, PluginPropertiesExtension extension) {
        File licenseFile = extension.getLicenseFile();
        if (licenseFile != null) {
            project.getTasks().named("bundlePlugin", Copy.class).configure(t -> {
                t.from(licenseFile.getParentFile(), spec -> {
                    spec.include(licenseFile.getName());
                    spec.rename(name -> "LICENSE.txt");
                });
            });
        }
        File noticeFile = extension.getNoticeFile();
        if (noticeFile != null) {
            TaskProvider<NoticeTask> generateNotice = project.getTasks().register("generateNotice", NoticeTask.class);
            generateNotice.configure(t -> {
                t.setInputFile(noticeFile);
                t.source(Util.getJavaMainSourceSet(project).get().getAllJava());
            });
            project.getTasks().named("bundlePlugin", Copy.class).configure(t -> {
                t.from(generateNotice);
            });
        }
    }
}

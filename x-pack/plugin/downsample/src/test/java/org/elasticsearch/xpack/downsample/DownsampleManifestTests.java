/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.downsample;

import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.persistent.PersistentTaskParams;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.plugins.DependencyInjectionManifest;
import org.elasticsearch.plugins.DependencyInjectionManifest.RegistryEntryInfo;
import org.elasticsearch.telemetry.TelemetryProvider;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Checks the dependency injection manifest the build generates for the downsample plugin, which is the only place the annotations on
 * {@link DownsampleMetrics}, {@link DownsampleShardTaskParams} and {@link DownsampleShardPersistentTaskState} have an observable effect.
 */
public class DownsampleManifestTests extends ESTestCase {

    @SuppressForbidden(reason = "reads manifest resources from the test classpath, which may be class directories or jars")
    private static DependencyInjectionManifest loadDownsampleManifest() throws IOException {
        List<DependencyInjectionManifest> matching = new ArrayList<>();
        List<URL> resources = Collections.list(
            DownsampleManifestTests.class.getClassLoader().getResources(DependencyInjectionManifest.RESOURCE_PATH)
        );
        for (URL resource : resources) {
            try (InputStream in = resource.openStream()) {
                DependencyInjectionManifest manifest = DependencyInjectionManifest.load(in);
                if (manifest.components().stream().anyMatch(c -> c.className().equals(DownsampleMetrics.class.getName()))) {
                    matching.add(manifest);
                }
            }
        }
        assertThat("manifests containing DownsampleMetrics among " + resources, matching.size(), equalTo(1));
        return matching.get(0);
    }

    public void testComponents() throws IOException {
        DependencyInjectionManifest manifest = loadDownsampleManifest();

        assertThat(manifest.components().size(), equalTo(1));
        DependencyInjectionManifest.ComponentInfo component = manifest.components().get(0);
        assertThat(component.className(), equalTo(DownsampleMetrics.class.getName()));
        assertThat(component.constructorParameterTypes(), contains(TelemetryProvider.class.getName()));
    }

    public void testRegistries() throws IOException {
        DependencyInjectionManifest manifest = loadDownsampleManifest();

        assertThat(manifest.registries().keySet(), equalTo(Set.of("named_writeable", "named_xcontent")));
        assertEntries(
            manifest.registries().get("named_writeable"),
            "<init>",
            Map.of(
                DownsampleShardPersistentTaskState.class,
                new Expected(DownsampleShardPersistentTaskState.NAME, PersistentTaskState.class),
                DownsampleShardTaskParams.class,
                new Expected(DownsampleShardTaskParams.NAME, PersistentTaskParams.class)
            )
        );
        assertEntries(
            manifest.registries().get("named_xcontent"),
            "fromXContent",
            Map.of(
                DownsampleShardPersistentTaskState.class,
                new Expected(DownsampleShardPersistentTaskState.NAME, PersistentTaskState.class),
                DownsampleShardTaskParams.class,
                new Expected(DownsampleShardTaskParams.NAME, PersistentTaskParams.class)
            )
        );
    }

    private record Expected(String name, Class<?> category) {}

    private static void assertEntries(List<RegistryEntryInfo> entries, String factory, Map<Class<?>, Expected> expected) {
        assertThat(
            entries.stream().map(RegistryEntryInfo::className).collect(Collectors.toSet()),
            equalTo(expected.keySet().stream().map(Class::getName).collect(Collectors.toSet()))
        );
        assertThat(entries.size(), equalTo(expected.size()));
        for (RegistryEntryInfo entry : entries) {
            assertThat(entry.className(), entry.factoryMethod(), equalTo(factory));
            Expected e = expected.entrySet()
                .stream()
                .filter(x -> x.getKey().getName().equals(entry.className()))
                .findFirst()
                .orElseThrow()
                .getValue();
            assertThat(entry.className(), entry.attributes().get("name"), equalTo(e.name()));
            assertThat(entry.className(), entry.attributes().get("category"), equalTo(e.category().getName()));
        }
    }
}

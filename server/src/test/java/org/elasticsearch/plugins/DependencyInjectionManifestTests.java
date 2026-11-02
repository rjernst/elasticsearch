/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugins;

import org.elasticsearch.plugins.DependencyInjectionManifest.ComponentInfo;
import org.elasticsearch.plugins.DependencyInjectionManifest.RegistryEntryInfo;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.jar.JarUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

public class DependencyInjectionManifestTests extends ESTestCase {

    static final String SAMPLE = """
        {
          "version": 1,
          "components": [
            {"class": "a.b.Foo", "constructor": ["a.b.Bar", "a.b.Baz"], "interfaces": ["a.b.Listener"]}
          ],
          "registries": {
            "named_writeable": [
              {"class": "a.b.Params", "factory": "<init>", "attributes": {"name": "params", "category": "a.b.Category"}}
            ],
            "persistent_task": [
              {"class": "a.b.Executor", "attributes": {"name": "my_task", "scope": "CLUSTER"}}
            ]
          }
        }
        """;

    private static DependencyInjectionManifest parse(String json) throws IOException {
        return DependencyInjectionManifest.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    public void testParse() throws IOException {
        DependencyInjectionManifest manifest = parse(SAMPLE);

        assertThat(
            manifest.components(),
            equalTo(List.of(new ComponentInfo("a.b.Foo", List.of("a.b.Bar", "a.b.Baz"), List.of("a.b.Listener"))))
        );
        assertThat(
            manifest.registries(),
            equalTo(
                Map.of(
                    "named_writeable",
                    List.of(new RegistryEntryInfo("a.b.Params", "<init>", Map.of("name", "params", "category", "a.b.Category"))),
                    "persistent_task",
                    List.of(new RegistryEntryInfo("a.b.Executor", null, Map.of("name", "my_task", "scope", "CLUSTER")))
                )
            )
        );
    }

    public void testParseMinimalAndUnknownKeys() throws IOException {
        assertThat(parse("{\"version\": 1}"), equalTo(DependencyInjectionManifest.EMPTY));
        assertThat(
            parse("{\"version\": 1, \"components\": [], \"registries\": {}, \"future\": {\"x\": [1]}}"),
            equalTo(DependencyInjectionManifest.EMPTY)
        );
    }

    public void testParseNonStringAttributes() throws IOException {
        DependencyInjectionManifest manifest = parse("""
            {"version": 1, "registries": {"r": [
              {"class": "a.B", "attributes": {"names": ["x", "y"], "count": 3, "flag": true}}
            ]}}
            """);
        assertThat(
            manifest.registries().get("r").get(0).attributes(),
            equalTo(Map.of("names", List.of("x", "y"), "count", 3, "flag", true))
        );
    }

    public void testUnsupportedVersion() {
        for (String json : List.of("{\"version\": 2}", "{}", "{\"version\": \"1\"}")) {
            IllegalStateException e = expectThrows(IllegalStateException.class, () -> parse(json));
            assertThat(e.getMessage(), containsString("Unsupported dependency injection manifest version"));
        }
    }

    public void testMalformed() {
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> parse("{\"version\": 1, \"components\": [{}]}"));
        assertThat(e.getMessage(), containsString("[class] must be a string"));
    }

    public void testMergeEmptyIdentity() throws IOException {
        DependencyInjectionManifest manifest = parse(SAMPLE);
        assertThat(manifest.merge(DependencyInjectionManifest.EMPTY), sameInstance(manifest));
        assertThat(DependencyInjectionManifest.EMPTY.merge(manifest), sameInstance(manifest));
        assertThat(DependencyInjectionManifest.EMPTY.merge(DependencyInjectionManifest.EMPTY), equalTo(DependencyInjectionManifest.EMPTY));
    }

    public void testMergeConcatenates() throws IOException {
        DependencyInjectionManifest first = parse(SAMPLE);
        DependencyInjectionManifest second = parse("""
            {"version": 1,
             "components": [{"class": "c.D", "constructor": [], "interfaces": []}],
             "registries": {
               "named_writeable": [{"class": "c.W", "factory": "read", "attributes": {"name": "w", "category": "a.b.Category"}}],
               "named_xcontent": [{"class": "c.X", "factory": "<init>", "attributes": {"name": "x"}}]
             }}
            """);

        DependencyInjectionManifest merged = first.merge(second);

        assertThat(merged.components().stream().map(ComponentInfo::className).toList(), equalTo(List.of("a.b.Foo", "c.D")));
        assertThat(
            merged.registries().get("named_writeable").stream().map(RegistryEntryInfo::className).toList(),
            equalTo(List.of("a.b.Params", "c.W"))
        );
        assertThat(merged.registries().get("persistent_task").size(), equalTo(1));
        assertThat(merged.registries().get("named_xcontent").size(), equalTo(1));
        assertThat(merged.registries().keySet(), equalTo(Set.of("named_writeable", "persistent_task", "named_xcontent")));
    }

    public void testLoadFromJars() throws IOException {
        Path dir = createTempDir();
        Path withManifest = dir.resolve("with.jar");
        Path withoutManifest = dir.resolve("without.jar");
        JarUtils.createJarWithEntries(
            withManifest,
            Map.of(DependencyInjectionManifest.RESOURCE_PATH, SAMPLE.getBytes(StandardCharsets.UTF_8))
        );
        JarUtils.createJarWithEntries(withoutManifest, Map.of("a/B.class", new byte[] { 1 }));

        assertThat(DependencyInjectionManifest.load(List.of(withManifest.toUri().toURL())), equalTo(parse(SAMPLE)));
        assertThat(DependencyInjectionManifest.load(List.of(withoutManifest.toUri().toURL())), equalTo(DependencyInjectionManifest.EMPTY));
        assertThat(
            DependencyInjectionManifest.load(List.<URL>of(withoutManifest.toUri().toURL(), withManifest.toUri().toURL())),
            equalTo(parse(SAMPLE))
        );
        assertThat(DependencyInjectionManifest.load(List.<URL>of()), equalTo(DependencyInjectionManifest.EMPTY));
    }

    public void testLoadFromJarWithBadVersionNamesJar() throws IOException {
        Path jar = createTempDir().resolve("bad.jar");
        JarUtils.createJarWithEntries(
            jar,
            Map.of(DependencyInjectionManifest.RESOURCE_PATH, "{\"version\": 7}".getBytes(StandardCharsets.UTF_8))
        );

        IllegalStateException e = expectThrows(
            IllegalStateException.class,
            () -> DependencyInjectionManifest.load(List.of(jar.toUri().toURL()))
        );
        assertThat(e.getMessage(), containsString("bad.jar"));
    }

    public void testLoadFromClassLoader() throws IOException {
        Path jar = createTempDir().resolve("with.jar");
        JarUtils.createJarWithEntries(jar, Map.of(DependencyInjectionManifest.RESOURCE_PATH, SAMPLE.getBytes(StandardCharsets.UTF_8)));

        try (URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {
            assertThat(DependencyInjectionManifest.load(loader), equalTo(parse(SAMPLE)));
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[0], null)) {
            assertThat(DependencyInjectionManifest.load(loader), equalTo(DependencyInjectionManifest.EMPTY));
        }
    }
}

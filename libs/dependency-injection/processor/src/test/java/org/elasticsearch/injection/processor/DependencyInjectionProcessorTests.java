/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tests that {@link DependencyInjectionProcessor} writes the manifest for valid components and registry entries
 * and reports errors for invalid ones.
 */
public class DependencyInjectionProcessorTests extends ProcessorTestCase {

    private static final String CATEGORY = """
        package test;
        public interface Category {}
        """;

    // ---- components

    public void testComponentWithOnePublicConstructor() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Bar", "package test; public class Bar {}");
        sources.put("test.Baz", "package test; public class Baz {}");
        sources.put("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {
                Foo(String ignored) {}
                public Foo(Bar bar, Baz baz, int count) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        assertEquals("""
            {
              "version": 1,
              "components": [
                {"class": "test.Foo", "constructor": ["test.Bar", "test.Baz", "int"], "interfaces": []}
              ],
              "registries": {}
            }
            """, result.manifest());
    }

    public void testComponentWithDefaultConstructor() throws Exception {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {}
            """);

        assertTrue(result.errors().toString(), result.success());
        assertTrue(result.manifest(), result.manifest().contains("{\"class\": \"test.Foo\", \"constructor\": [], \"interfaces\": []}"));
    }

    public void testComponentWithInjectConstructor() throws Exception {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            import org.elasticsearch.injection.api.Inject;
            @Component
            public class Foo {
                public Foo() {}
                @Inject
                public Foo(String name) {}
            }
            """);

        assertTrue(result.errors().toString(), result.success());
        assertTrue(result.manifest(), result.manifest().contains("\"constructor\": [\"java.lang.String\"]"));
    }

    public void testComponentWithTwoConstructorsAndNoInjectFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {
                public Foo() {}
                public Foo(String name) {}
            }
            """);

        assertErrorContaining(result, "component [test.Foo] must have exactly one public constructor");
        assertErrorContaining(result, "Foo(java.lang.String)");
    }

    public void testComponentWithTwoInjectConstructorsFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            import org.elasticsearch.injection.api.Inject;
            @Component
            public class Foo {
                @Inject
                public Foo() {}
                @Inject
                public Foo(String name) {}
            }
            """);

        assertErrorContaining(result, "component [test.Foo] must have exactly one public constructor");
    }

    public void testComponentWithoutPublicConstructorFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {
                private Foo() {}
            }
            """);

        assertErrorContaining(result, "public constructors: none");
    }

    public void testParameterizedConstructorParameterFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import java.util.List;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {
                public Foo(List<String> names) {}
            }
            """);

        assertErrorContaining(
            result,
            "parameterized type java.util.List<java.lang.String> is not injectable; declare a named interface instead"
        );
    }

    public void testArrayOfParameterizedConstructorParameterFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import java.util.List;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo {
                public Foo(List<String>[] names) {}
            }
            """);

        assertErrorContaining(result, "parameterized type java.util.List<java.lang.String>[] is not injectable");
    }

    public void testTypeVariableConstructorParameterFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo<T> {
                public Foo(T value) {}
            }
            """);

        assertErrorContaining(result, "type variable T is not injectable");
    }

    public void testAbstractComponentFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public abstract class Foo {}
            """);

        assertErrorContaining(result, "@Component can only be applied to a non-abstract class");
    }

    public void testInterfaceComponentFails() {
        CompilationResult result = compile("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public interface Foo {}
            """);

        assertErrorContaining(result, "@Component can only be applied to a non-abstract class");
    }

    public void testComponentInterfacesIncludeThoseOfSupertypes() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Base", "package test; public abstract class Base implements Runnable, java.io.Serializable {}");
        sources.put("test.Extended", "package test; public interface Extended extends Comparable<Extended> {}");
        sources.put("test.Foo", """
            package test;
            import org.elasticsearch.injection.api.Component;
            @Component
            public class Foo extends Base implements Extended, Runnable {
                public void run() {}
                public int compareTo(Extended other) { return 0; }
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        assertTrue(
            result.manifest(),
            result.manifest()
                .contains("\"interfaces\": [\"java.io.Serializable\", \"java.lang.Comparable\", \"java.lang.Runnable\", \"test.Extended\"]")
        );
    }

    public void testNestedComponentUsesBinaryName() throws Exception {
        CompilationResult result = compile("test.Outer", """
            package test;
            import org.elasticsearch.injection.api.Component;
            public class Outer {
                public static class Inner {}
                @Component
                public static class Nested {
                    public Nested(Inner inner) {}
                }
            }
            """);

        assertTrue(result.errors().toString(), result.success());
        assertTrue(
            result.manifest(),
            result.manifest().contains("{\"class\": \"test.Outer$Nested\", \"constructor\": [\"test.Outer$Inner\"]")
        );
    }

    // ---- registry entries

    public void testEntryWithMarkedConstructor() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(String in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        assertEquals(
            String.join(
                "\n",
                "{",
                "  \"version\": 1,",
                "  \"components\": [],",
                "  \"registries\": {",
                "    \"sample\": [",
                "      {\"class\": \"test.Params\", \"factory\": \"<init>\", \"attributes\": {\"name\": \"params\", "
                    + "\"category\": \"test.Category\", \"deprecatedNames\": [], \"kind\": \"ONE\", \"weight\": 1}}",
                "    ]",
                "  }",
                "}",
                ""
            ),
            result.manifest()
        );
    }

    public void testEntryWithMarkedStaticFactory() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleKind;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class, deprecatedNames = { "a", "b\\"c" }, kind = SampleKind.TWO, weight = 7)
            public class Params implements Category {
                private Params() {}
                @SampleReader
                public static Category read(String in) { return new Params(); }
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        assertTrue(
            result.manifest(),
            result.manifest()
                .contains(
                    "{\"class\": \"test.Params\", \"factory\": \"read\", \"attributes\": {\"name\": \"params\", "
                        + "\"category\": \"test.Category\", \"deprecatedNames\": [\"a\", \"b\\\"c\"], \"kind\": \"TWO\", \"weight\": 7}}"
                )
        );
    }

    public void testRepeatableContainerWithTwoEntries() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "one", category = Category.class)
            @SampleEntry(name = "two", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(String in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        String manifest = result.manifest();
        int one = manifest.indexOf("\"name\": \"one\"");
        int two = manifest.indexOf("\"name\": \"two\"");
        assertTrue(manifest, one > 0 && two > one);
    }

    public void testEntryTypeWithoutFactoryMarker() throws Exception {
        CompilationResult result = compile("test.Task", """
            package test;
            import org.elasticsearch.injection.processor.sample.PlainEntry;
            @PlainEntry(name = "my_task")
            public class Task {}
            """);

        assertTrue(result.errors().toString(), result.success());
        assertEquals("""
            {
              "version": 1,
              "components": [],
              "registries": {
                "plain": [
                  {"class": "test.Task", "attributes": {"name": "my_task"}}
                ]
              }
            }
            """, result.manifest());
    }

    public void testEntryMissingFactoryMarkerFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                public Params(String in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertErrorContaining(
            result,
            "must have exactly one constructor or static method annotated with @org.elasticsearch.injection.processor.sample.SampleReader"
        );
    }

    public void testEntryWithTwoFactoriesFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(String in) {}
                @SampleReader
                public static Params read(String in) { return null; }
            }
            """);

        assertErrorContaining(compile(sources), "found 2");
    }

    public void testFactoryWithWrongParameterCountFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params() {}
            }
            """);

        assertErrorContaining(compile(sources), "must have exactly one parameter");
    }

    public void testFactoryMethodMustBeStatic() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params read(String in) { return this; }
            }
            """);

        assertErrorContaining(compile(sources), "must be static");
    }

    public void testFactoryMethodMustReturnAnnotatedType() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public static String read(String in) { return in; }
            }
            """);

        assertErrorContaining(compile(sources), "must return [test.Params] or a supertype of it");
    }

    public void testCategoryNotASupertypeFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params {
                @SampleReader
                public Params(String in) {}
            }
            """);

        assertErrorContaining(compile(sources), "[test.Params] must be a subtype of its category [test.Category]");
    }

    public void testEntryOnInterfaceFails() {
        CompilationResult result = compile("test.Task", """
            package test;
            import org.elasticsearch.injection.processor.sample.PlainEntry;
            @PlainEntry(name = "my_task")
            public interface Task {}
            """);

        assertErrorContaining(result, "can only be applied to a class");
    }

    public void testDuplicateEntryFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        for (String name : new String[] { "First", "Second" }) {
            sources.put("test." + name, """
                package test;
                import org.elasticsearch.injection.processor.sample.SampleEntry;
                import org.elasticsearch.injection.processor.sample.SampleReader;
                @SampleEntry(name = "params", category = Category.class)
                public class $CLASS implements Category {
                    @SampleReader
                    public $CLASS(String in) {}
                }
                """.replace("$CLASS", name));
        }

        assertErrorContaining(compile(sources), "duplicate [sample] entry with category [test.Category] and name [params]");
    }

    public void testDeprecatedNameCollidingWithNameFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.First", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class First implements Category {
                @SampleReader
                public First(String in) {}
            }
            """);
        sources.put("test.Second", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "other", category = Category.class, deprecatedNames = { "params" })
            public class Second implements Category {
                @SampleReader
                public Second(String in) {}
            }
            """);

        assertErrorContaining(compile(sources), "and name [params]");
    }

    public void testSameNameInDifferentRegistriesIsNotADuplicate() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.PlainEntry;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "same", category = Category.class)
            @PlainEntry(name = "same")
            public class Params implements Category {
                @SampleReader
                public Params(String in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
    }

    // ---- output

    public void testNoAnnotationsWritesNoManifest() throws Exception {
        CompilationResult result = compile("test.Plain", "package test; public class Plain {}");

        assertTrue(result.errors().toString(), result.success());
        assertNull(result.manifest());
    }

    public void testErrorsWriteNoManifest() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Good", "package test; @org.elasticsearch.injection.api.Component public class Good {}");
        sources.put("test.Bad", "package test; @org.elasticsearch.injection.api.Component public abstract class Bad {}");

        CompilationResult result = compile(sources);

        assertFalse(result.success());
        assertNull(result.manifest());
    }

    public void testOutputIsDeterministic() throws Exception {
        String component = """
            package test;
            @org.elasticsearch.injection.api.Component
            public class $CLASS {}
            """;
        String entry = """
            package test;
            @org.elasticsearch.injection.processor.sample.PlainEntry(name = "$NAME")
            public class $CLASS {}
            """;
        String[] names = { "Alpha", "Beta", "Gamma" };

        Map<String, String> forward = new LinkedHashMap<>();
        Map<String, String> backward = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String reverse = names[names.length - 1 - i];
            forward.put("test.C" + name, component.replace("$CLASS", "C" + name));
            forward.put("test.E" + name, entry.replace("$NAME", name.toLowerCase(Locale.ROOT)).replace("$CLASS", "E" + name));
            backward.put("test.C" + reverse, component.replace("$CLASS", "C" + reverse));
            backward.put("test.E" + reverse, entry.replace("$NAME", reverse.toLowerCase(Locale.ROOT)).replace("$CLASS", "E" + reverse));
        }

        String first = compile(forward).manifest();
        String second = compile(forward).manifest();
        String reordered = compile(backward).manifest();

        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(first, reordered);
        assertTrue(first, first.indexOf("test.CAlpha") < first.indexOf("test.CBeta"));
    }
}

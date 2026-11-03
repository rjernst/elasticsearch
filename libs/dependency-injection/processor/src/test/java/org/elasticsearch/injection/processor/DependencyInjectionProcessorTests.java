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
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(SampleInput in) {}
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
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class, deprecatedNames = { "a", "b\\"c" }, kind = SampleKind.TWO, weight = 7)
            public class Params implements Category {
                private Params() {}
                @SampleReader
                public static Params read(SampleInput in) { return new Params(); }
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
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "one", category = Category.class)
            @SampleEntry(name = "two", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(SampleInput in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
        String manifest = result.manifest();
        int one = manifest.indexOf("\"name\": \"one\"");
        int two = manifest.indexOf("\"name\": \"two\"");
        assertTrue(manifest, one > 0 && two > one);
    }

    public void testEntryMissingFactoryMarkerFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                public Params(SampleInput in) {}
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
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params(SampleInput in) {}
                @SampleReader
                public static Params read(SampleInput in) { return null; }
            }
            """);

        assertErrorContaining(compile(sources), "found 2");
    }

    public void testFactoryMethodMustBeStatic() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
                @SampleReader
                public Params read(SampleInput in) { return this; }
            }
            """);

        assertErrorContaining(compile(sources), "must be static");
    }

    public void testCategoryNotASupertypeFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class)
            public class Params {
                @SampleReader
                public Params(SampleInput in) {}
            }
            """);

        assertErrorContaining(compile(sources), "[test.Params] must be a subtype of its category [test.Category]");
    }

    public void testEntryOnInterfaceFails() {
        CompilationResult result = compile("test.Task", """
            package test;
            import org.elasticsearch.injection.processor.sample.OtherEntry;
            @OtherEntry(name = "my_task")
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
                import org.elasticsearch.injection.processor.sample.SampleInput;
                @SampleEntry(name = "params", category = Category.class)
                public class $CLASS implements Category {
                    @SampleReader
                    public $CLASS(SampleInput in) {}
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
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "params", category = Category.class)
            public class First implements Category {
                @SampleReader
                public First(SampleInput in) {}
            }
            """);
        sources.put("test.Second", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "other", category = Category.class, deprecatedNames = { "params" })
            public class Second implements Category {
                @SampleReader
                public Second(SampleInput in) {}
            }
            """);

        assertErrorContaining(compile(sources), "and name [params]");
    }

    public void testSameNameInDifferentRegistriesIsNotADuplicate() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.OtherEntry;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            @SampleEntry(name = "same", category = Category.class)
            @OtherEntry(name = "same")
            public class Params implements Category {
                @SampleReader
                public Params(SampleInput in) {}
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
    }

    // ---- factory adaptability

    /** Compiles a {@code Params} class registered as a {@code SampleEntry}, whose members are {@code members}. */
    private CompilationResult compileParams(String members) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import java.io.IOException;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params implements Category {
            $MEMBERS
            }
            """.replace("$MEMBERS", members));
        return compile(sources);
    }

    /**
     * Compiles an {@code Item} registered through a registry declared in the sources, whose factory interface is
     * {@code factory} and whose annotated member is {@code members}.
     */
    private CompilationResult compileCustomFactory(String factory, String members) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Factory", "package test;\n" + factory);
        sources.put("test.Marker", """
            package test;
            import java.lang.annotation.*;
            @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD })
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Marker {}
            """);
        sources.put("test.Entry", """
            package test;
            import java.lang.annotation.*;
            import org.elasticsearch.injection.api.RegistryEntryType;
            @RegistryEntryType(key = "custom", factoryMarker = Marker.class, factory = Factory.class)
            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Entry {}
            """);
        sources.put("test.Item", """
            package test;
            import java.io.IOException;
            @Entry
            public class Item {
            $MEMBERS
            }
            """.replace("$MEMBERS", members));
        return compile(sources);
    }

    public void testConstructorAcceptingSupertypeOfInputIsAdaptable() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(Object in) {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryWithWrongParameterTypeFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(String in) {}
            """);

        assertErrorContaining(result, "[test.Params] member [Params(java.lang.String)] cannot be adapted to");
        assertErrorContaining(
            result,
            "parameter 1 must accept [org.elasticsearch.injection.processor.sample.SampleInput] but is [java.lang.String]"
        );
    }

    public void testFactoryWithTooFewParametersFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params() {}
            """);

        assertErrorContaining(result, "expected 1 parameter(s)");
        assertErrorContaining(result, "but found 0");
    }

    public void testFactoryWithTooManyParametersFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(SampleInput in, int extra) {}
            """);

        assertErrorContaining(result, "but found 2");
    }

    public void testStaticFactoryWithUnrelatedReturnTypeFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public static String read(SampleInput in) { return ""; }
            """);

        assertErrorContaining(result, "result [java.lang.String] is not assignable to [test.Params]");
    }

    public void testStaticFactoryReturningSupertypeFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public static Category read(SampleInput in) { return null; }
            """);

        assertErrorContaining(result, "result [test.Category] is not assignable to [test.Params]");
    }

    public void testFactoryThrowingDeclaredCheckedExceptionIsAdaptable() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(SampleInput in) throws IOException {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryThrowingSubtypeOfDeclaredExceptionIsAdaptable() {
        CompilationResult result = compileParams("""
            @SampleReader
            public static Params read(SampleInput in) throws java.io.FileNotFoundException { return null; }
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryThrowingUncheckedExceptionIsAdaptable() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(SampleInput in) throws IllegalStateException, AssertionError {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryThrowingUndeclaredCheckedExceptionFails() {
        CompilationResult result = compileParams("""
            @SampleReader
            public Params(SampleInput in) throws java.util.concurrent.TimeoutException {}
            """);

        assertErrorContaining(result, "throws [java.util.concurrent.TimeoutException] which the interface method does not declare");
    }

    public void testFactoryInterfaceWithTwoAbstractMethodsFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(String in);
                T other(String in);
            }
            """, """
            @Marker
            public Item(String in) {}
            """);

        assertErrorContaining(result, "factory [test.Factory] of the registry entry annotation for [test.Item]");
        assertErrorContaining(result, "must be an interface with exactly one abstract method");
    }

    public void testFactoryInterfaceWithNoAbstractMethodFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory {
                default void create(String in) {}
            }
            """, """
            @Marker
            public Item(String in) {}
            """);

        assertErrorContaining(result, "must be an interface with exactly one abstract method");
    }

    public void testFactoryThatIsNotAnInterfaceFails() {
        CompilationResult result = compileCustomFactory("""
            public abstract class Factory {
                abstract Object create(String in);
            }
            """, """
            @Marker
            public Item(String in) {}
            """);

        assertErrorContaining(result, "must be an interface with exactly one abstract method");
    }

    public void testFactoryInterfaceWithDefaultStaticAndObjectMethodsIsAdaptable() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(String in);
                default T createTwice(String in) { create(in); return create(in); }
                static void helper() {}
                boolean equals(Object other);
                String toString();
            }
            """, """
            @Marker
            public Item(String in) {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryInterfaceMethodImplementedByDefaultInSubinterfaceIsIgnored() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> extends Base<T> {
                T create(String in);
                default T legacy(String in) { return create(in); }
            }
            interface Base<T> {
                T legacy(String in);
            }
            """, """
            @Marker
            public Item(String in) {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryInterfaceTypeVariableReturnIsBoundToAnnotatedClass() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(String in);
            }
            """, """
            @Marker
            public static Item create(String in) { return new Item(); }
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testStaticFactoryReturningUnrelatedTypeForBoundVariableFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(String in);
            }
            """, """
            @Marker
            public static Object create(String in) { return null; }
            """);

        assertErrorContaining(result, "result [java.lang.Object] is not assignable to [test.Item]");
    }

    public void testFactoryInterfaceWithUnboundParameterTypeVariableFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(T in);
            }
            """, """
            @Marker
            public Item(Object in) {}
            """);

        assertErrorContaining(
            result,
            "parameter type [T] of the factory method is a type variable; declare a named interface that binds it"
        );
    }

    public void testFactoryInterfaceWithNestedTypeVariableParameterFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                T create(java.util.List<? extends T> in);
            }
            """, """
            @Marker
            public Item(java.util.List<?> in) {}
            """);

        assertErrorContaining(result, "is a type variable; declare a named interface that binds it");
    }

    public void testFactoryInterfaceWithTypeVariableThrownTypeFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T, E extends Exception> {
                T create(String in) throws E;
            }
            """, """
            @Marker
            public Item(String in) throws IOException {}
            """);

        assertErrorContaining(result, "thrown type [E] of the factory method is a type variable");
    }

    public void testFactoryInterfaceReturningTypeVariableContainerFails() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                java.util.List<T> create(String in);
            }
            """, """
            @Marker
            public static java.util.List<Item> create(String in) { return null; }
            """);

        assertErrorContaining(result, "return type [java.util.List<T>] of the factory method uses a type variable");
    }

    public void testGenericAnnotatedClassWithStaticFactoryIsAdaptable() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Category", CATEGORY);
        sources.put("test.Params", """
            package test;
            import org.elasticsearch.injection.processor.sample.SampleEntry;
            import org.elasticsearch.injection.processor.sample.SampleInput;
            import org.elasticsearch.injection.processor.sample.SampleReader;
            @SampleEntry(name = "params", category = Category.class)
            public class Params<T> implements Category {
                @SampleReader
                public static <T> Params<T> read(SampleInput in) { return null; }
            }
            """);

        CompilationResult result = compile(sources);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testFactoryThatIsNotAClassOrInterfaceFails() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("test.Marker", """
            package test;
            import java.lang.annotation.*;
            @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD })
            public @interface Marker {}
            """);
        sources.put("test.Entry", """
            package test;
            import java.lang.annotation.*;
            import org.elasticsearch.injection.api.RegistryEntryType;
            @RegistryEntryType(key = "custom", factoryMarker = Marker.class, factory = int.class)
            @Target(ElementType.TYPE)
            public @interface Entry {}
            """);
        sources.put("test.Item", """
            package test;
            @Entry
            public class Item {
                @Marker
                public Item() {}
            }
            """);

        assertErrorContaining(compile(sources), "factory [not a class or interface]");
    }

    public void testFactoryInterfaceWithNonVariableGenericReturnIsChecked() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> {
                java.util.List<String> create(String in);
            }
            """, """
            @Marker
            public static java.util.List<String> create(String in) { return null; }
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testInheritedAbstractMethodResolvedThroughTypeArguments() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> extends Base<T, java.io.IOException> {}
            interface Base<R, E extends Exception> {
                R apply(String in) throws E;
            }
            """, """
            @Marker
            public Item(String in) throws IOException {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testInheritedAbstractMethodRedeclaredIsASingleMethod() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> extends Base<T, java.io.IOException> {
                @Override
                T apply(String in) throws java.io.IOException;
            }
            interface Base<R, E extends Exception> {
                R apply(String in) throws E;
            }
            """, """
            @Marker
            public Item(String in) throws IOException {}
            """);

        assertTrue(result.errors().toString(), result.success());
    }

    public void testInheritedAbstractMethodStillChecksExceptions() {
        CompilationResult result = compileCustomFactory("""
            public interface Factory<T> extends Base<T, IllegalStateException> {}
            interface Base<R, E extends Exception> {
                R apply(String in) throws E;
            }
            """, """
            @Marker
            public Item(String in) throws IOException {}
            """);

        assertErrorContaining(result, "throws [java.io.IOException] which the interface method does not declare");
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
            @org.elasticsearch.injection.processor.sample.OtherEntry(name = "$NAME")
            public class $CLASS {
                @org.elasticsearch.injection.processor.sample.SampleReader
                public $CLASS(org.elasticsearch.injection.processor.sample.SampleInput in) {}
            }
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

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.common.io.stream;

import org.elasticsearch.injection.api.RegistryEntryType;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that the annotated class is a {@link NamedWriteable} registered under {@link #name()} within
 * {@link #category()}.
 * <p>
 * Declaring the entry on the class itself lets the build record it in the dependency injection manifest, so the
 * {@link NamedWriteableRegistry} entries of a node can be derived without each plugin listing them by hand. The class
 * must be a subtype of {@link #category()} and have exactly one constructor or static method marked
 * {@link WriteableReader}, which must be adaptable to {@link Writeable.Reader} and is the reader used to deserialize it.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Repeatable(NamedWriteableEntries.class)
@RegistryEntryType(key = "named_writeable", factoryMarker = WriteableReader.class, factory = Writeable.Reader.class)
public @interface NamedWriteableEntry {

    /** The name the entry is registered under within its category. */
    String name();

    /** The category (base type) the entry is registered in. */
    Class<?> category();
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.api;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Meta-annotation that declares an annotation to be a registry entry declaration.
 * <p>
 * Each registry (for example named writeables, or persistent tasks) defines its own annotation, so that the
 * attributes of an entry are type checked where it is declared. Marking that annotation with
 * {@code RegistryEntryType} is all that is needed for the build time processor to collect its uses into the
 * manifest under {@link #key()}, without the processor knowing anything about the individual registry. Entries are
 * created through a member of the annotated class marked by {@link #factoryMarker()}, which must be adaptable to
 * {@link #factory()}.
 */
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface RegistryEntryType {

    /** The name of the registry in the manifest that entries of the annotated annotation are recorded under. */
    String key();

    /**
     * An annotation that marks the constructor or static method used to create instances of an annotated class.
     * Exactly one such member must exist on the annotated class.
     */
    Class<? extends Annotation> factoryMarker();

    /**
     * The functional interface the registry builds from the marked member, for example {@code Writeable.Reader}.
     * <p>
     * The build proves that the marked member is adaptable to the single abstract method of this interface: it
     * accepts the arguments that method receives, returns something assignable to its result and throws no checked
     * exception it does not declare. A result that is a type variable of the interface is bound to the annotated
     * class. Any other type variable in the method's parameters is an error; declare a named interface that binds it.
     */
    Class<?> factory();
}

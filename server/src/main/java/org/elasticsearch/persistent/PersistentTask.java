/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.persistent;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that the annotated class is the executor of the persistent task named {@link #name()}.
 * <p>
 * Declaring the task on the executor itself keeps its name and scope next to the code that runs it. This is a plain
 * annotation: it is not a registry entry and the build does not record it in the dependency injection manifest.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface PersistentTask {

    /** The name of the persistent task. */
    String name();

    /** Whether the task is tracked per project or once for the whole cluster. */
    Scope scope();

    /** The level at which a persistent task is tracked. */
    enum Scope {
        PROJECT,
        CLUSTER
    }
}

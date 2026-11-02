/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a class as a node-scoped singleton that should be discovered at build time.
 * <p>
 * Discovery happens in the compiler, via an annotation processor, so that the set of components is known
 * without scanning class files at node startup. The annotated class must be concrete and have a single
 * constructor that can be used for injection: either its only public constructor, or the only public
 * constructor annotated with {@link Inject}.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Component {
}

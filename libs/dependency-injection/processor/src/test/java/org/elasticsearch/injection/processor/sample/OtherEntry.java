/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor.sample;

import org.elasticsearch.injection.api.RegistryEntryType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** A second registry entry declaration, with no category, so tests can use more than one registry. */
@RegistryEntryType(key = "other", factoryMarker = SampleReader.class, factory = SampleFactory.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface OtherEntry {
    String name();
}

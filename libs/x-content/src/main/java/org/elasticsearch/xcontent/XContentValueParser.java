/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.xcontent;

import org.elasticsearch.core.CheckedFunction;

import java.io.IOException;

/**
 * Parses a value from an {@link XContentParser}. This is the shape of the member marked {@link XContentReader}: a
 * constructor or static method taking the parser. It names the {@link CheckedFunction} used by
 * {@link NamedXContentRegistry.Entry} so that the build can check a marked member against a single, non-generic
 * exception type.
 */
@FunctionalInterface
public interface XContentValueParser<T> extends CheckedFunction<XContentParser, T, IOException> {

    @Override
    T apply(XContentParser parser) throws IOException;
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import junit.framework.TestCase;

import java.util.List;

public class JsonWriterTests extends TestCase {

    public void testFiniteNumbersAreWritten() {
        assertEquals("[1, 2.5, 3.5]", new JsonWriter().value(List.of(1, 2.5d, 3.5f)).toString());
    }

    public void testNonFiniteNumbersAreRejected() {
        for (Object value : new Object[] {
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY }) {
            IllegalArgumentException e = expectThrows(value);
            assertTrue(e.getMessage(), e.getMessage().contains("non-finite number [" + value + "]"));
        }
    }

    private static IllegalArgumentException expectThrows(Object value) {
        try {
            new JsonWriter().value(List.of(value));
        } catch (IllegalArgumentException e) {
            return e;
        }
        throw new AssertionError("expected an exception for [" + value + "]");
    }
}

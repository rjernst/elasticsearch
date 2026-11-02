/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.processor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A minimal JSON writer, so the processor needs no dependencies. Supports exactly the value types the
 * manifest contains: strings, numbers, booleans, lists and string keyed maps of those.
 */
final class JsonWriter {

    private final StringBuilder out = new StringBuilder();

    /** Appends already formatted JSON structure (braces, separators, whitespace). */
    JsonWriter raw(String text) {
        out.append(text);
        return this;
    }

    JsonWriter value(Object value) {
        if (value instanceof String s) {
            string(s);
        } else if (value instanceof Double d && Double.isFinite(d) == false || value instanceof Float f && Float.isFinite(f) == false) {
            // NaN and the infinities have no JSON representation
            throw new IllegalArgumentException("non-finite number [" + value + "] cannot be written as JSON");
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                value(list.get(i));
            }
            out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (first == false) {
                    out.append(", ");
                }
                first = false;
                string(entry.getKey().toString());
                out.append(": ");
                value(entry.getValue());
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("unsupported JSON value [" + value + "]");
        }
        return this;
    }

    private void string(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}

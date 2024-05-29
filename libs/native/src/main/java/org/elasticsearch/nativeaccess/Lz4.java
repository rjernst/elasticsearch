/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess;

import org.elasticsearch.nativeaccess.lib.Lz4Library;

import java.nio.ByteBuffer;
import java.util.Objects;

public class Lz4 {

    private final Lz4Library lz4Lib;

    public Lz4(Lz4Library lz4Lib) {
        this.lz4Lib = lz4Lib;
    }

    /**
     * Compress the content of {@code src} into {@code dst}, and return the number of compressed bytes.
     * {@link ByteBuffer#position()} and {@link ByteBuffer#limit()} of both {@link ByteBuffer}s are left unmodified.
     */
    public int compress(CloseableByteBuffer src, CloseableByteBuffer dst) {
        Objects.requireNonNull(src, "Null source buffer");
        Objects.requireNonNull(dst, "Null destination buffer");
        int ret = lz4Lib.compressDefault(src, dst);
        if (ret == 0) {
            throw new RuntimeException("Failed to compress bytes with LZ4");
        }
        return ret;
    }

    /**
     * Decompress the content of {@code src} into {@code dst}, and return the number of decompressed bytes.
     * {@link ByteBuffer#position()} and {@link ByteBuffer#limit()} of both {@link ByteBuffer}s are left unmodified.
     */
    public int decompress(CloseableByteBuffer src, CloseableByteBuffer dst) {
        Objects.requireNonNull(src, "Null source buffer");
        Objects.requireNonNull(dst, "Null destination buffer");
        int ret = lz4Lib.decompressSafe(src, dst);
        if (ret == 0) {
            throw new RuntimeException("Failed to decompress bytes with LZ4");
        }
        return ret;
    }

    /**
     * Return the maximum number of compressed bytes given an input length.
     */
    public int compressBound(int srcLen) {
        int size = lz4Lib.compressBound(srcLen);
        if (size == 0) {
            throw new IllegalArgumentException("Bad src length: " + srcLen);
        }
        return size;
    }
}

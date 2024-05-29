/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess.lib;

import org.elasticsearch.nativeaccess.CloseableByteBuffer;

/**
 * LZ4 native library.
 *
 * @see <a href="https://fossies.org/linux/lz4/doc/lz4_manual.html">docs</a>
 */
public non-sealed interface Lz4Library extends NativeLibrary {

    /**
     * Maps to {@code LZ4_compressBound}
     */
    int compressBound(int srcLen);

    /**
     * Maps to {@code LZ4_compress_default}
     */
    int compressDefault(CloseableByteBuffer src, CloseableByteBuffer dst);

    /**
     * Maps to {@code LZ4_decompress_safe}
     */
    int decompressSafe(CloseableByteBuffer src, CloseableByteBuffer dst);
}

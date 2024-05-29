/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess.jna;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import org.elasticsearch.nativeaccess.CloseableByteBuffer;
import org.elasticsearch.nativeaccess.lib.Lz4Library;

class JnaLz4Library implements Lz4Library {

    public static class NativeFunctions {
        public static native int LZ4_compressBound(int scrLen);

        public static native int LZ4_compress_default(Pointer src, Pointer dst, int srcSize, int dstCapacity);

        public static native int LZ4_decompress_safe(Pointer src, Pointer dst, int compressedSize, int dstCapacity);
    }

    JnaLz4Library() {
        Native.register(NativeFunctions.class, "lz4");
    }

    @Override
    public int compressBound(int srcLen) {
        return NativeFunctions.LZ4_compressBound(srcLen);
    }

    @Override
    public int compressDefault(CloseableByteBuffer src, CloseableByteBuffer dst) {
        assert dst instanceof JnaCloseableByteBuffer;
        assert src instanceof JnaCloseableByteBuffer;
        var nativeDst = (JnaCloseableByteBuffer) dst;
        var nativeSrc = (JnaCloseableByteBuffer) src;
        return NativeFunctions.LZ4_compress_default(
            nativeSrc.memory.share(src.buffer().position()),
            nativeDst.memory.share(dst.buffer().position()),
            src.buffer().remaining(),
            dst.buffer().remaining()
        );
    }

    @Override
    public int decompressSafe(CloseableByteBuffer src, CloseableByteBuffer dst) {
        assert dst instanceof JnaCloseableByteBuffer;
        assert src instanceof JnaCloseableByteBuffer;
        var nativeDst = (JnaCloseableByteBuffer) dst;
        var nativeSrc = (JnaCloseableByteBuffer) src;
        return NativeFunctions.LZ4_decompress_safe(
            nativeSrc.memory.share(src.buffer().position()),
            nativeDst.memory.share(dst.buffer().position()),
            src.buffer().remaining(),
            dst.buffer().remaining()
        );
    }
}

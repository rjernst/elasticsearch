/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess.jdk;

import org.elasticsearch.nativeaccess.CloseableByteBuffer;
import org.elasticsearch.nativeaccess.lib.Lz4Library;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.elasticsearch.nativeaccess.jdk.LinkerHelper.downcallHandle;

class JdkLz4Library implements Lz4Library {

    static {
        System.loadLibrary("lz4");
    }

    private static final MethodHandle compressBound$mh = downcallHandle("LZ4_compressBound", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle compressDefault$mh = downcallHandle(
        "LZ4_compress_default",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT)
    );
    private static final MethodHandle decompressSafe$mh = downcallHandle(
        "LZ4_decompress_safe",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT)
    );

    @Override
    public int compressBound(int srcLen) {
        try {
            return (int) compressBound$mh.invokeExact(srcLen);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Override
    public int compressDefault(CloseableByteBuffer src, CloseableByteBuffer dst) {
        assert dst instanceof JdkCloseableByteBuffer;
        assert src instanceof JdkCloseableByteBuffer;
        var nativeDst = (JdkCloseableByteBuffer) dst;
        var nativeSrc = (JdkCloseableByteBuffer) src;
        var dstSize = dst.buffer().remaining();
        var srcSize = src.buffer().remaining();
        var segmentDst = nativeDst.segment.asSlice(dst.buffer().position(), dstSize);
        var segmentSrc = nativeSrc.segment.asSlice(src.buffer().position(), srcSize);
        try {
            return (int) compressDefault$mh.invokeExact(segmentSrc, segmentDst, srcSize, dstSize);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Override
    public int decompressSafe(CloseableByteBuffer src, CloseableByteBuffer dst) {
        assert dst instanceof JdkCloseableByteBuffer;
        assert src instanceof JdkCloseableByteBuffer;
        var nativeDst = (JdkCloseableByteBuffer) dst;
        var nativeSrc = (JdkCloseableByteBuffer) src;
        var dstSize = dst.buffer().remaining();
        var srcSize = src.buffer().remaining();
        var segmentDst = nativeDst.segment.asSlice(dst.buffer().position(), dstSize);
        var segmentSrc = nativeSrc.segment.asSlice(src.buffer().position(), srcSize);
        try {
            return (int) decompressSafe$mh.invokeExact(segmentSrc, segmentDst, srcSize, dstSize);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}

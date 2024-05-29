/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess;

import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;

import java.util.Arrays;

import static org.hamcrest.Matchers.equalTo;

public class Lz4Tests extends ESTestCase {
    static NativeAccess nativeAccess;
    static Lz4 lz4;

    @BeforeClass
    public static void getLz4() {
        nativeAccess = NativeAccess.instance();
        lz4 = nativeAccess.getLz4();
    }

    public void testCompressBound() {
        assertThat(lz4.compressBound(0), Matchers.greaterThanOrEqualTo(1));
        assertThat(lz4.compressBound(100), Matchers.greaterThanOrEqualTo(100));
        expectThrows(IllegalArgumentException.class, () -> lz4.compressBound(Integer.MAX_VALUE));
        expectThrows(IllegalArgumentException.class, () -> lz4.compressBound(-1));
        expectThrows(IllegalArgumentException.class, () -> lz4.compressBound(-100));
        expectThrows(IllegalArgumentException.class, () -> lz4.compressBound(Integer.MIN_VALUE));
    }

    public void testCompressValidation() {
        try (var src = nativeAccess.newBuffer(1000); var dst = nativeAccess.newBuffer(1)) {
            var npe1 = expectThrows(NullPointerException.class, () -> lz4.compress(null, dst));
            assertThat(npe1.getMessage(), equalTo("Null source buffer"));
            var npe2 = expectThrows(NullPointerException.class, () -> lz4.compress(src, null));
            assertThat(npe2.getMessage(), equalTo("Null destination buffer"));

            var srcBuf = src.buffer();
            // dst capacity too low
            for (int i = 0; i < srcBuf.remaining(); ++i) {
                srcBuf.put(i, randomByte());
            }
            var e = expectThrows(RuntimeException.class, () -> lz4.compress(src, dst));
            assertThat(e.getMessage(), equalTo("Failed to compress bytes with LZ4"));
        }
    }

    public void testDecompressValidation() {
        try (
            var original = nativeAccess.newBuffer(1000);
            var compressed = nativeAccess.newBuffer(1);
        ) {
            var npe1 = expectThrows(NullPointerException.class, () -> lz4.decompress(null, original));
            assertThat(npe1.getMessage(), equalTo("Null source buffer"));
            var npe2 = expectThrows(NullPointerException.class, () -> lz4.decompress(compressed, null));
            assertThat(npe2.getMessage(), equalTo("Null destination buffer"));
        }
    }

    public void testOneByte() {
        doTestRoundtrip(new byte[] { 'z' });
    }

    public void testConstant() {
        byte[] b = new byte[randomIntBetween(100, 1000)];
        Arrays.fill(b, randomByte());
        doTestRoundtrip(b);
    }

    public void testCycle() {
        byte[] b = new byte[randomIntBetween(100, 1000)];
        for (int i = 0; i < b.length; ++i) {
            b[i] = (byte) (i & 0x0F);
        }
        doTestRoundtrip(b);
    }

    private void doTestRoundtrip(byte[] data) {
        try (
            var original = nativeAccess.newBuffer(data.length);
            var compressed = nativeAccess.newBuffer(lz4.compressBound(data.length));
            var restored = nativeAccess.newBuffer(data.length)
        ) {
            original.buffer().put(0, data);
            int compressedLength = lz4.compress(original, compressed);
            compressed.buffer().limit(compressedLength);
            int decompressedLength = lz4.decompress(compressed, restored);
            assertThat(restored.buffer(), equalTo(original.buffer()));
            assertThat(decompressedLength, equalTo(data.length));
        }

        // Now with non-zero offsets
        final int compressedOffset = randomIntBetween(1, 1000);
        final int decompressedOffset = randomIntBetween(1, 1000);
        try (
            var original = nativeAccess.newBuffer(decompressedOffset + data.length);
            var compressed = nativeAccess.newBuffer(compressedOffset + lz4.compressBound(data.length));
            var restored = nativeAccess.newBuffer(decompressedOffset + data.length)
        ) {
            original.buffer().put(decompressedOffset, data);
            original.buffer().position(decompressedOffset);
            compressed.buffer().position(compressedOffset);
            int compressedLength = lz4.compress(original, compressed);
            compressed.buffer().limit(compressedOffset + compressedLength);
            restored.buffer().position(decompressedOffset);
            int decompressedLength = lz4.decompress(compressed, restored);
            assertThat(decompressedLength, equalTo(data.length));
            assertThat(
                restored.buffer().slice(decompressedOffset, data.length),
                equalTo(original.buffer().slice(decompressedOffset, data.length))
            );
        }
    }
}

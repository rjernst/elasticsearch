/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess;

import org.elasticsearch.logging.LogManager;
import org.elasticsearch.logging.Logger;
import org.elasticsearch.nativeaccess.lib.JavaLibrary;
import org.elasticsearch.nativeaccess.lib.NativeLibraryProvider;
import org.elasticsearch.nativeaccess.lib.ZstdLibrary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class AbstractNativeAccess implements NativeAccess {

    protected static final Logger logger = LogManager.getLogger(NativeAccess.class);

    private final String name;
    private final JavaLibrary javaLib;
    private final Zstd zstd;
    protected boolean isMemoryLocked = false;
    protected ExecSandboxState execSandboxState = ExecSandboxState.NONE;

    protected AbstractNativeAccess(String name, NativeLibraryProvider libraryProvider) {
        this.name = name;
        this.javaLib = libraryProvider.getLibrary(JavaLibrary.class);
        this.zstd = new Zstd(libraryProvider.getLibrary(ZstdLibrary.class));
    }

    String getName() {
        return name;
    }

    @Override
    public Systemd systemd() {
        return null;
    }

    @Override
    public Zstd getZstd() {
        return zstd;
    }

    @Override
    public CloseableByteBuffer newBuffer(int len) {
        assert len > 0;
        return javaLib.newBuffer(len);
    }

    @Override
    public boolean isMemoryLocked() {
        return isMemoryLocked;
    }

    @Override
    public ExecSandboxState getExecSandboxState() {
        return execSandboxState;
    }

    @Override
    public void preallocate(Path path, long fileSize) throws IOException {
        // fallback implementation in Java when native allocation doesn't work
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            if (raf.length() != fileSize) {
                logger.info("pre-allocating file [{}] ({} bytes) using setLength method", path, fileSize);
                raf.setLength(fileSize);
                logger.debug("pre-allocated file [{}] using setLength method", path);
            }
        } catch (final Exception e) {
            logger.warn(() -> "failed to pre-allocate file [" + path + "] using setLength method", e);
            // if anything goes wrong, delete the potentially created file to not waste disk space
            Files.deleteIfExists(path);
        }
    }
}

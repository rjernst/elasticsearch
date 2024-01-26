/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess.jna;

import com.sun.jna.Native;

import org.elasticsearch.jdk.JdkUtils;
import org.elasticsearch.nativeaccess.jna.JnaStaticPosixCLibrary.JnaRLimit;
import org.elasticsearch.nativeaccess.lib.PosixCLibrary;

class JnaPosixCLibrary implements PosixCLibrary {

    static {
        JdkUtils.ensureInitialized(JnaStaticPosixCLibrary.class);
    }

    @Override
    public int mlockall(int flags) {
        return JnaStaticPosixCLibrary.mlockall(flags);
    }

    @Override
    public int geteuid() {
        return JnaStaticPosixCLibrary.geteuid();
    }

    @Override
    public RLimit newRLimit() {
        return new JnaRLimit();
    }

    @Override
    public int getrlimit(int resource, RLimit rlimit) {
        assert rlimit instanceof JnaRLimit;
        var jnaRlimit = (JnaRLimit) rlimit;
        return JnaStaticPosixCLibrary.getrlimit(resource, jnaRlimit);
    }

    @Override
    public int setrlimit(int resource, RLimit rlimit) {
        assert rlimit instanceof JnaRLimit;
        var jnaRlimit = (JnaRLimit) rlimit;
        return JnaStaticPosixCLibrary.setrlimit(resource, jnaRlimit);
    }

    @Override
    public String strerror(int errno) {
        return JnaStaticPosixCLibrary.strerror(errno);
    }

    @Override
    public int errno() {
        return Native.getLastError();
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.classscanner;

import org.objectweb.asm.ModuleVisitor;

import java.util.List;

class DelegatingModuleVisitor extends ModuleVisitor {

    private final List<ModuleVisitor> delegate;

    private DelegatingModuleVisitor(int api, List<ModuleVisitor> delegates) {
        super(api);
        this.delegate = delegates;
    }

    static ModuleVisitor maybeWrap(int api, List<ModuleVisitor> delegates) {
        if (delegates.isEmpty()) {
            return null;
        } else if (delegates.size() == 1) {
            return delegates.getFirst();
        } else {
            return new DelegatingModuleVisitor(api, delegates);
        }
    }

    @Override
    public void visitMainClass(String mainClass) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitMainClass(mainClass);
        }
    }

    @Override
    public void visitPackage(String packaze) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitPackage(packaze);
        }
    }

    @Override
    public void visitRequire(String module, int access, String version) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitRequire(module, access, version);
        }
    }

    @Override
    public void visitExport(String packaze, int access, String... modules) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitExport(packaze, access, modules);
        }
    }

    @Override
    public void visitOpen(String packaze, int access, String... modules) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitOpen(packaze, access, modules);
        }
    }

    @Override
    public void visitUse(String service) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitUse(service);
        }
    }

    @Override
    public void visitProvide(String service, String... providers) {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitProvide(service, providers);
        }
    }

    @Override
    public void visitEnd() {
        for (ModuleVisitor delegate : delegate) {
            delegate.visitEnd();
        }
    }
}

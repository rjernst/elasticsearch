/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.classscanner;

import org.objectweb.asm.AnnotationVisitor;

import java.util.ArrayList;
import java.util.List;

class DelegatingAnnotationVisitor extends AnnotationVisitor {
    private final List<AnnotationVisitor> delegates;

    private DelegatingAnnotationVisitor(int api, List<AnnotationVisitor> delegates) {
        super(api);
        this.delegates = delegates;
    }

    static AnnotationVisitor maybeWrap(int api, List<AnnotationVisitor> delegates) {
        if (delegates.isEmpty()) {
            return null;
        } else if (delegates.size() == 1) {
            return delegates.getFirst();
        } else {
            return new DelegatingAnnotationVisitor(api, delegates);
        }
    }

    @Override
    public void visit(String name, Object value) {
        for (AnnotationVisitor visitor : delegates) {
            visitor.visit(name, value);
        }
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        for (AnnotationVisitor visitor : delegates) {
            visitor.visitEnum(name, descriptor, value);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (AnnotationVisitor delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitAnnotation(name, descriptor);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (AnnotationVisitor delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitArray(name);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitEnd() {
        for (AnnotationVisitor delegate : delegates) {
            delegate.visitEnd();
        }
    }
}

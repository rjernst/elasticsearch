/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.List;

public class DelegatingRecordComponentVisitor extends RecordComponentVisitor {

    private final List<RecordComponentVisitor> delegates;

    protected DelegatingRecordComponentVisitor(int api, List<RecordComponentVisitor> delegates) {
        super(api);
        this.delegates = delegates;
    }

    public static RecordComponentVisitor maybeWrap(int api, List<RecordComponentVisitor> delegates) {
        if (delegates.isEmpty()) {
            return null;
        } else if (delegates.size() == 1) {
            return delegates.getFirst();
        } else {
            return new DelegatingRecordComponentVisitor(api, delegates);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitAnnotation(descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        for (var delegate : delegates) {
            delegate.visitAttribute(attribute);
        }
    }

    @Override
    public void visitEnd() {
        for (var delegate : delegates) {
            delegate.visitEnd();
        }
    }
}

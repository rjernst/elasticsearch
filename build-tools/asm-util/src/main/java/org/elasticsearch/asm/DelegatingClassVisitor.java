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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.List;

public class DelegatingClassVisitor extends ClassVisitor {

    private final List<ClassVisitor> delegates;

    protected DelegatingClassVisitor(int api, List<ClassVisitor> delegates) {
        super(api);
        this.delegates = delegates;
    }

    public List<ClassVisitor> getDelegates() {
        return delegates;
    }

    public static ClassVisitor maybeWrap(int api, List<ClassVisitor> delegates) {
        if (delegates.isEmpty()) {
            return null;
        } else if (delegates.size() == 1) {
            return delegates.getFirst();
        } else {
            return new DelegatingClassVisitor(api, delegates);
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        for (var delegate : getDelegates()) {
            delegate.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public void visitSource(String source, String debug) {
        for (var delegate : getDelegates()) {
            delegate.visitSource(source, debug);
        }
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        List<ModuleVisitor> visitors = new ArrayList<>();
        for (var delegate : getDelegates()) {
            ModuleVisitor visitor = delegate.visitModule(name, access, version);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingModuleVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitNestHost(String nestHost) {
        for (var delegate : getDelegates()) {
            delegate.visitNestHost(nestHost);
        }
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        for (var delegate : getDelegates()) {
            delegate.visitOuterClass(owner, name, descriptor);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : getDelegates()) {
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
        for (var delegate : getDelegates()) {
            AnnotationVisitor visitor = delegate.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        for (var delegate : getDelegates()) {
            delegate.visitAttribute(attribute);
        }
    }

    @Override
    public void visitNestMember(String nestMember) {
        for (var delegate : getDelegates()) {
            delegate.visitNestMember(nestMember);
        }
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        for (var delegate : getDelegates()) {
            delegate.visitPermittedSubclass(permittedSubclass);
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        for (var delegate : getDelegates()) {
            delegate.visitInnerClass(name, outerName, innerName, access);
        }
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        List<RecordComponentVisitor> visitors = new ArrayList<>();
        for (var delegate : getDelegates()) {
            RecordComponentVisitor visitor = delegate.visitRecordComponent(name, descriptor, signature);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingRecordComponentVisitor.maybeWrap(api, visitors);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        List<FieldVisitor> visitors = new ArrayList<>();
        for (var delegate : getDelegates()) {
            FieldVisitor visitor = delegate.visitField(access, name, descriptor, signature, value);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingFieldVisitor.maybeWrap(api, visitors);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        List<MethodVisitor> visitors = new ArrayList<>();
        for (var delegate : getDelegates()) {
            MethodVisitor visitor = delegate.visitMethod(access, name, descriptor, signature, exceptions);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingMethodVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitEnd() {
        for (var delegate : getDelegates()) {
            delegate.visitEnd();
        }
    }
}

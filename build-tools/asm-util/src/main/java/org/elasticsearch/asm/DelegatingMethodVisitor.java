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
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.List;

public class DelegatingMethodVisitor extends MethodVisitor {

    private final List<MethodVisitor> delegates;

    protected DelegatingMethodVisitor(int api, List<MethodVisitor> delegate) {
        super(api);
        this.delegates = delegate;
    }

    public static MethodVisitor maybeWrap(int api, List<MethodVisitor> delegates) {
        if (delegates.isEmpty()) {
            return null;
        } else if (delegates.size() == 1) {
            return delegates.getFirst();
        } else {
            return new DelegatingMethodVisitor(api, delegates);
        }
    }

    @Override
    public void visitParameter(String name, int access) {
        for (var delegate : delegates) {
            delegate.visitParameter(name, access);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitAnnotationDefault();
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
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
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
        for (var delegate : delegates) {
            delegate.visitAnnotableParameterCount(parameterCount, visible);
        }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitParameterAnnotation(parameter, descriptor, visible);
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
    public void visitCode() {
        for (var delegate : delegates) {
            delegate.visitCode();
        }
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        for (var delegate : delegates) {
            delegate.visitFrame(type, numLocal, local, numStack, stack);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        for (var delegate : delegates) {
            delegate.visitInsn(opcode);
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        for (var delegate : delegates) {
            delegate.visitIntInsn(opcode, operand);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        for (var delegate : delegates) {
            delegate.visitVarInsn(opcode, var);
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        for (var delegate : delegates) {
            delegate.visitTypeInsn(opcode, type);
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        for (var delegate : delegates) {
            delegate.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
        for (var delegate : delegates) {
            delegate.visitMethodInsn(opcode, owner, name, descriptor);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        for (var delegate : delegates) {
            delegate.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        for (var delegate : delegates) {
            delegate.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        for (var delegate : delegates) {
            delegate.visitJumpInsn(opcode, label);
        }
    }

    @Override
    public void visitLabel(Label label) {
        for (var delegate : delegates) {
            delegate.visitLabel(label);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        for (var delegate : delegates) {
            delegate.visitLdcInsn(value);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        for (var delegate : delegates) {
            delegate.visitIincInsn(varIndex, increment);
        }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        for (var delegate : delegates) {
            delegate.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        for (var delegate : delegates) {
            delegate.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        for (var delegate : delegates) {
            delegate.visitMultiANewArrayInsn(descriptor, numDimensions);
        }
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        for (var delegate : delegates) {
            delegate.visitTryCatchBlock(start, end, handler, type);
        }
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        for (var delegate : delegates) {
            delegate.visitLocalVariable(name, descriptor, signature, start, end, index);
        }
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(
        int typeRef,
        TypePath typePath,
        Label[] start,
        Label[] end,
        int[] index,
        String descriptor,
        boolean visible) {
        List<AnnotationVisitor> visitors = new ArrayList<>();
        for (var delegate : delegates) {
            AnnotationVisitor visitor = delegate.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
            if (visitor != null) {
                visitors.add(visitor);
            }
        }
        return DelegatingAnnotationVisitor.maybeWrap(api, visitors);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        for (var delegate : delegates) {
            delegate.visitLineNumber(line, start);
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        for (var delegate : delegates) {
            delegate.visitMaxs(maxStack, maxLocals);
        }
    }

    @Override
    public void visitEnd() {
        for (var delegate : delegates) {
            delegate.visitEnd();
        }
    }
}

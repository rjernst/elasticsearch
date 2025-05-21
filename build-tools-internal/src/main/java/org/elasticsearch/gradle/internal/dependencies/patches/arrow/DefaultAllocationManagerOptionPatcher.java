/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencies.patches.arrow;

import org.elasticsearch.gradle.internal.dependencies.patches.MethodReplacement;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class DefaultAllocationManagerOptionPatcher extends ClassVisitor {

    DefaultAllocationManagerOptionPatcher(ClassWriter classWriter) {
        super(Opcodes.ASM9, classWriter);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        System.out.println("Visiting method: " + name);
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (name.equals("getDefaultAllocationManagerFactory")) {
            return new MethodReplacement(mv, () -> {
                System.out.println("Replacing method: " + name);
                // now add our explicit initialization of DEFAULT_ALLOCATION_MANAGER_FACTORY
                var factoryType = Type.getObjectType("org/apache/arrow/memory/AllocationManager$Factory");
                var implClass = "org/elasticsearch/xpack/esql/arrow/AllocationManagerShim";
                mv.visitFieldInsn(Opcodes.GETSTATIC, implClass, "INSTANCE", factoryType.getDescriptor());
                mv.visitInsn(Opcodes.ARETURN);
            });
        }
        return mv;
    }
}

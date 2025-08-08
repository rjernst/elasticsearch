/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.transport;

import org.elasticsearch.gradle.classscanner.ClassVisitorTask;
import org.elasticsearch.gradle.internal.transport.TransportVersionUtils.TransportVersionReference;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * This task locates all method invocations of org.elasticsearch.TransportVersion#fromName(java.lang.String) in the
 * provided directory, and then records the value of string literals passed as arguments. It then records each
 * string on a newline along with path and line number in the provided output file.
 */
@CacheableTask
public abstract class CollectTransportVersionReferencesTask extends ClassVisitorTask {
    public static final String TRANSPORT_VERSION_SET_CLASS = "org/elasticsearch/TransportVersion";
    public static final String TRANSPORT_VERSION_SET_METHOD_NAME = "fromName";

    @Override
    public String getVisitorClassName() {
        return TransportVersionReferenceCollector.class.getName();
    }

    /**
     * The output file, with each newline containing the string literal argument of each method
     * invocation.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void collectTransportVersions() throws IOException {

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        try (OutputStream output = Files.newOutputStream(outputFile)) {
            boolean isFirst = true;
            for (File stateFile : getVisitorStateFiles()) {
                Path path = stateFile.toPath();
                if (Files.exists(path) == false) {
                    continue;
                }
                if (isFirst == false) {
                    output.write(System.lineSeparator().getBytes());
                }
                isFirst = false;
                try (InputStream input = Files.newInputStream(path)) {
                    input.transferTo(output);
                }
            }
        }
    }

    public static class TransportVersionReferenceCollector extends ClassVisitor implements Closeable {
        private final Set<TransportVersionReference> results = new HashSet<>();
        private final Path outputFile;
        private String classname;

        public TransportVersionReferenceCollector(Path outputFile) {
            super(Opcodes.ASM9);
            this.outputFile = outputFile;
        }

        @Override
        public void close() throws IOException {
            if (results.isEmpty() == false) {
                Files.writeString(outputFile, String.join("\n", results.stream().map(Object::toString).sorted().toList()));
            }
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.classname = name.replace('/', '.');
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
                int lineNumber = -1;

                @Override
                public void visitLineNumber(int line, Label start) {
                    lineNumber = line;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (owner.equals(TRANSPORT_VERSION_SET_CLASS) && name.equals(TRANSPORT_VERSION_SET_METHOD_NAME)) {
                        var abstractInstruction = this.instructions.getLast();
                        String location = classname + " line " + lineNumber;
                        if (abstractInstruction instanceof LdcInsnNode ldcInsnNode
                            && ldcInsnNode.cst instanceof String tvName
                            && tvName.isEmpty() == false) {
                            results.add(new TransportVersionReference(tvName, location));
                        } else {
                            // The instruction is not a LDC with a String constant (or an empty String), which is not allowed.
                            throw new RuntimeException(
                                "TransportVersion.fromName must be called with a non-empty String literal. " + "See " + location + "."
                            );
                        }
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }
    }
}

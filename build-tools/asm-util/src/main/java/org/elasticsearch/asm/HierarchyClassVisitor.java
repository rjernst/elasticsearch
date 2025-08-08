/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.asm;

import org.objectweb.asm.ClassVisitor;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HierarchyClassVisitor extends ClassVisitor {

    private final Map<String, Set<String>> classToSubclasses = new HashMap<>();
    private static final String OBJECT_NAME = Object.class.getCanonicalName().replace('.', '/');

    public HierarchyClassVisitor(int api) {
        super(api);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (OBJECT_NAME.equals(superName) == false) {
            classToSubclasses.computeIfAbsent(superName, k -> new HashSet<>()).add(name);
        }

        for (String iface : interfaces) {
            classToSubclasses.computeIfAbsent(iface, k -> new HashSet<>()).add(name);
        }
    }

    public Map<String, Set<String>> getDescendants(Collection<String> classes) {
        Map<String, Set<String>> descendants = new HashMap<>();
        Deque<Map.Entry<String, String>> queue = new ArrayDeque<>();

        // initialize queue with each class we care about, mapping to itself
        for (String classname : classes) {
            queue.addLast(Map.entry(classname, classname));
        }

        // pull from queue and re-add subclasses of the entry
        while (queue.isEmpty() == false) {
            Map.Entry<String, String> e = queue.removeFirst();
            String classname = e.getKey();

            // this is our base case. eventually we will find leaves in the hierarchy and nothing more will be added to the queue
            Set<String> subclasses = classToSubclasses.get(classname);
            if (subclasses == null) {
                continue;
            }

            for (String subclass : subclasses) {
                descendants.computeIfAbsent(subclass, k -> new HashSet<>()).add(e.getValue());
                queue.addLast(Map.entry(subclass, classname));
            }
        }

        return descendants;
    }
}

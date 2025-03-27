/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugins;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class FunctionalLambdaGenerator {
    private final MethodHandles.Lookup lookup;
    private final String interfaceMethodName;
    private final MethodType interfaceMethodType;

    public FunctionalLambdaGenerator(Class<?> functionalInterface) {
        this.lookup = MethodHandles.publicLookup();

        if (functionalInterface.isInterface() == false) {
            throw new IllegalArgumentException(functionalInterface.getName() + " is not an interface");
        }
        Method interfaceMethod = null;

        for (Method method : functionalInterface.getDeclaredMethods()) {
            if (method.isDefault()) {
                continue;
            }
            if (interfaceMethod != null) {
                throw new IllegalArgumentException(functionalInterface.getName() + " is not a functional interface");
            }
            interfaceMethod = method;
        }

        interfaceMethodName = interfaceMethod.getName();
        interfaceMethodType = MethodType.methodType(interfaceMethod.getReturnType(), interfaceMethod.getParameterTypes());
    }

    public CallSite generate(Class<?> clazz, String methodName, MethodType methodType) {
        MethodHandle mh;
        try {
            if (methodName.equals("<init>")) {
                mh = lookup.findConstructor(clazz, methodType);
            } else {
                mh = lookup.findStatic(clazz, methodName, methodType);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }

        try {
            return LambdaMetafactory.metafactory(lookup, interfaceMethodName, interfaceMethodType, methodType.generic(), mh, methodType);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }
}

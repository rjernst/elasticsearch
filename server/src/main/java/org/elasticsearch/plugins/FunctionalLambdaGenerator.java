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
    private final Class<?> functionalInterface;
    private final String interfaceMethodName;
    private final Class<?>[] interfaceMethodParameters;

    public FunctionalLambdaGenerator(Class<?> functionalInterface) {
        this.lookup = MethodHandles.publicLookup();
        this.functionalInterface = functionalInterface;

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
        interfaceMethodParameters = interfaceMethod.getParameterTypes();
    }

    public CallSite generate(Class<?> clazz, String methodName, MethodType methodType) {
        MethodHandle mh;
        try {
            if (methodName.equals("<init>")) {
                mh = lookup.findConstructor(clazz, MethodType.methodType(void.class, methodType.parameterArray()));
            } else {
                mh = lookup.findStatic(clazz, methodName, methodType);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
        var lambdaFactoryMethodType = MethodType.methodType(functionalInterface);
        var interfaceMethodType = MethodType.methodType(clazz, interfaceMethodParameters);

        try {
            return LambdaMetafactory.metafactory(MethodHandles.lookup(), interfaceMethodName, lambdaFactoryMethodType, interfaceMethodType, mh, interfaceMethodType);
        } catch (Throwable e) {
            throw new AssertionError("interface method: " + interfaceMethodType +"\nlambda mt: " + MethodType.methodType(functionalInterface) + "\ninterface mt: " + interfaceMethodType, e);
        }
    }

    public static MethodHandle findMethod(Class<?> clazz, String methodName, MethodType methodType) {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            if (methodName.equals("<init>")) {
                return lookup.findConstructor(clazz, MethodType.methodType(void.class, methodType.parameterArray()));
            } else {
                return lookup.findStatic(clazz, methodName, methodType);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}

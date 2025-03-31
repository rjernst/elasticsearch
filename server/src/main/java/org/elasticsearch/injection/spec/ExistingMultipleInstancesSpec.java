/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.injection.spec;

import java.util.Collection;

/**
 * Indicates that a type should be injected by passing a particular collection {@link #instances}.
 */
public record ExistingMultipleInstancesSpec(Class<?> requestedType, Collection<Object> instances) implements UnambiguousSpec {
    @Override
    public String toString() {
        // Don't call instance.toString; who knows what that will return
        return "ExistingMultipleInstanceSpec[" + "requestedType=" + requestedType + ']';
    }

    public void addInstances(Collection<?> instances) {
        this.instances.addAll(instances);
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.arrow;

import org.apache.arrow.memory.AllocationManager;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.DefaultAllocationManagerOption;

/**
 * An Arrow memory allocation manager that always fails.
 * <p>
 * We don't actually use Arrow's memory manager as we stream dataframe buffers directly from ESQL blocks.
 * But Arrow won't initialize properly unless it has one (and requires either the arrow-memory-netty or arrow-memory-unsafe libraries).
 * It also does some fancy classpath scanning and calls to {@code setAccessible} which will be rejected by the security manager.
 * <p>
 * So we configure an allocation manager that will fail on any attempt to allocate memory.
 * <p>
 * Note this is installed as the default at build time through patching the arrow jar.
 *
 * @see DefaultAllocationManagerOption
 */
public class AllocationManagerShim implements AllocationManager.Factory {

    public static final AllocationManager.Factory INSTANCE = new AllocationManagerShim();

    @Override
    public AllocationManager create(BufferAllocator accountingAllocator, long size) {
        throw new UnsupportedOperationException("Arrow memory manager is disabled");
    }

    @Override
    public ArrowBuf empty() {
        throw new UnsupportedOperationException("Arrow memory manager is disabled");
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.persistent;

import org.elasticsearch.plugin.NamedComponent;

import java.util.concurrent.Executor;

public abstract class InjectablePersistentTasksExecutor<Params extends PersistentTaskParams> extends PersistentTasksExecutor<Params> {

    protected InjectablePersistentTasksExecutor(Executor executor) {
        super("", executor);
    }

    @Override
    public String getTaskName() {
        // TODO: cache this
        NamedComponent[] annotationsByType = this.getClass().getAnnotationsByType(NamedComponent.class);
        if (annotationsByType.length != 1) {
            throw new AssertionError("Missing NamedComponent annotation on " + this.getClass().getName());
        }
        return annotationsByType[0].value();
    }
}

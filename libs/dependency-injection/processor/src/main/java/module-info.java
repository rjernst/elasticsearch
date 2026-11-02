/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

/**
 * Annotation processor that records the dependency injection participants of a compilation (components and
 * registry entries) in a single manifest resource.
 */
module org.elasticsearch.injection.processor {
    requires java.compiler;

    provides javax.annotation.processing.Processor with org.elasticsearch.injection.processor.DependencyInjectionProcessor;
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

/**
 * Annotations that declare dependency injection participants and registry entries. They live in their own
 * dependency-free module so that any module, including the low level libraries, can use them.
 */
module org.elasticsearch.injection.api {
    exports org.elasticsearch.injection.api;
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugins;

import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xcontent.XContentParserConfiguration.EMPTY;

public record BundleManifest(List<String> componentClasses, List<String> extensionsFields) {
    private static final String FILENAME = "bundle-manifest.json";
    public static final BundleManifest EMPTY = new BundleManifest(List.of(), List.of());

    public static BundleManifest load(Path dir) {
        Path manifestPath = dir.resolve(FILENAME);
        if (Files.exists(manifestPath)) {
            try (var stream = Files.newInputStream(manifestPath)) {
                return load(stream);
            } catch (IOException e){
                throw new UncheckedIOException(e);
            }
        }
        return EMPTY;
    }

    public static BundleManifest load(InputStream stream) {
        try (var parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY, stream)) {

            Map<String, Object> manifestMap = parser.map();
            @SuppressWarnings("unchecked")
            List<String> components = (List<String>) manifestMap.get("components");
            @SuppressWarnings("unchecked")
            List<String> extensionsFields = (List<String>) manifestMap.get("extensions_fields");

            return new BundleManifest(components, extensionsFields);
        } catch (IOException e){
            throw new UncheckedIOException(e);
        }
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.attachment;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.ingest.RandomDocumentPicks;
import org.elasticsearch.ingest.attachment.spi.TikaParserProvider;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SampleFileTestCase extends ESTestCase {
    protected Processor processor;

    public abstract String getResourcePath();

    public List<TikaParserProvider> additionalParsers() {
        return List.of();
    }

    @Before
    public void createStandardProcessor() throws Exception {
        AttachmentProcessor.Factory factory = new AttachmentProcessor.Factory(additionalParsers());
        processor = factory.create(Map.of(), randomAlphaOfLength(10), null, new HashMap<>(Map.of("field", "source_field",
            "target_field", "target_field", "indexed_chars", 10000)));
    }

    protected Map<String, Object> parseDocument(String file, Processor attachmentProcessor) throws Exception {
        return parseDocument(file, attachmentProcessor, new HashMap<>());
    }

    protected Map<String, Object> parseDocument(String file, Processor attachmentProcessor, Map<String, Object> optionalFields)
        throws Exception {
        return parseDocument(file, attachmentProcessor, optionalFields, false);
    }

    protected Map<String, Object> parseDocument(
        String file,
        Processor attachmentProcessor,
        Map<String, Object> optionalFields,
        boolean includeResourceName
    ) throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("source_field", getAsBinaryOrBase64(file));
        if (includeResourceName) {
            document.put("resource_name", file);
        }
        document.putAll(optionalFields);

        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);
        attachmentProcessor.execute(ingestDocument);

        @SuppressWarnings("unchecked")
        Map<String, Object> attachmentData = (Map<String, Object>) ingestDocument.getSourceAndMetadata().get("target_field");
        return attachmentData;
    }

    private Object getAsBinaryOrBase64(String filename) throws Exception {
        String path = getResourcePath() + filename;
        try (InputStream is = getClass().getResourceAsStream(path)) {
            byte bytes[] = IOUtils.toByteArray(is);
            // behave like CBOR from time to time
            if (rarely()) {
                return bytes;
            } else {
                return Base64.getEncoder().encodeToString(bytes);
            }
        }
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.attachment.epub;

import org.elasticsearch.ingest.attachment.SampleFileTestCase;
import org.elasticsearch.ingest.attachment.spi.TikaParserProvider;

import java.util.List;
import java.util.Map;

public class EpubTikaParserTests extends SampleFileTestCase {

    @Override
    public List<TikaParserProvider> additionalParsers() {
        return List.of(new EpubTikaParsers());
    }

    @Override
    public String getResourcePath() {
        return "/sample-files/";
    }

    public void testEpubDocument() throws Exception {
        Map<String, Object> attachmentData = parseDocument("testEPUB.epub", processor);
        assertNotNull(attachmentData);
    }
}

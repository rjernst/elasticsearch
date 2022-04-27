/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.attachment;

import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.tika.metadata.Metadata;
import org.elasticsearch.core.PathUtils;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Evil test-coverage cheat, we parse a bunch of docs from tika
 * so that we have a nice grab-bag variety, and assert some content
 * comes back and no exception.
 */
@SuppressFileSystems("ExtrasFS") // don't try to parse extraN
public abstract class TikaDocTestCase extends ESTestCase {

    TikaImpl tika;

    @Before
    public void setupTika() {
        tika = new TikaImpl(List.of());
    }

    public abstract String getResourcePath();



    public void testFiles() throws Exception {
        Path tmp = createTempDir();
        String tikaFiles = getResourcePath();
        logger.debug("unzipping all tika sample files");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathUtils.get(getClass().getResource(tikaFiles).toURI()))) {
            for (Path doc : stream) {
                String filename = doc.getFileName().toString();
                TestUtil.unzip(getClass().getResourceAsStream(tikaFiles + filename), tmp);
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmp)) {
            for (Path doc : stream) {
                logger.debug("parsing: {}", doc);
                assertParseable(doc);
            }
        }
    }

    void assertParseable(Path fileName) throws Exception {
        try {
            byte bytes[] = Files.readAllBytes(fileName);
            String parsedContent = tika.parse(bytes, new Metadata(), -1);
            assertNotNull(parsedContent);
            assertFalse(parsedContent.isEmpty());
            logger.debug("extracted content: {}", parsedContent);
        } catch (Exception e) {
            throw new RuntimeException("parsing of filename: " + fileName.getFileName() + " failed", e);
        }
    }
}

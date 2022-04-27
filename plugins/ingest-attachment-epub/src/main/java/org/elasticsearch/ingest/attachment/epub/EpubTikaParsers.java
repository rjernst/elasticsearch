/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.attachment.epub;

import org.apache.tika.parser.Parser;
import org.elasticsearch.ingest.attachment.spi.TikaParserProvider;

import java.util.List;

public class EpubTikaParsers implements TikaParserProvider {
    @Override
    public List<Parser> getParsers() {
        return List.of(new org.apache.tika.parser.epub.EpubParser());
    }
}

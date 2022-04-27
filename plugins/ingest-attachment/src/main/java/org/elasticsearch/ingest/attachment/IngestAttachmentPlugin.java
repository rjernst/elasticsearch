/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.ingest.attachment;

import org.elasticsearch.ingest.Processor;
import org.elasticsearch.ingest.attachment.spi.TikaParserProvider;
import org.elasticsearch.plugins.ExtensiblePlugin;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IngestAttachmentPlugin extends Plugin implements IngestPlugin, ExtensiblePlugin {

    List<TikaParserProvider> parserProviders = new ArrayList<>();

    @Override
    public void loadExtensions(ExtensionLoader loader) {
        parserProviders.addAll(loader.loadExtensions(TikaParserProvider.class));
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        return Collections.singletonMap(AttachmentProcessor.TYPE, new AttachmentProcessor.Factory(parserProviders));
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.transport;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * A build service to make accessing and transport version resources in a gradle context easier.
 */
public abstract class TransportVersionResourcesService extends TransportVersionResources
    implements
        BuildService<TransportVersionResourcesService.Parameters> {

    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getTransportResourcesDirectory();

        DirectoryProperty getRootDirectory();
    }

    @Inject
    public abstract ExecOperations getExecOperations();

    @Inject
    public TransportVersionResourcesService(Parameters params) {
        super(directoryPath(params.getTransportResourcesDirectory()), directoryPath(params.getRootDirectory()));
    }

    private static Path directoryPath(DirectoryProperty directory) {
        return directory.get().getAsFile().toPath();
    }

    @Override
    protected String gitCommand(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        List<String> command = new ArrayList<>();
        Collections.addAll(command, "git", "-C", getTransportResourcesDir().toString());
        Collections.addAll(command, args);

        ExecResult result = getExecOperations().exec(spec -> {
            spec.setCommandLine(command);
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stdout);
            spec.setIgnoreExitValue(true);
        });

        if (result.getExitValue() != 0) {
            throw new RuntimeException(
                "git command failed with exit code "
                    + result.getExitValue()
                    + System.lineSeparator()
                    + "command: "
                    + String.join(" ", command)
                    + System.lineSeparator()
                    + "output:"
                    + System.lineSeparator()
                    + stdout.toString(StandardCharsets.UTF_8)
            );
        }

        return stdout.toString(StandardCharsets.UTF_8);
    }
}

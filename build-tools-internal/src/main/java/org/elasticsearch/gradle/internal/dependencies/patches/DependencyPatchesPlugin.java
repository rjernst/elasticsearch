/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.dependencies.patches;

import org.elasticsearch.gradle.internal.dependencies.patches.DependencyPatchTransform.PatchInfo;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;

public class DependencyPatchesPlugin implements Plugin<Project> {

    private static final Map<String, List<PatchInfo>> patches = Map.ofEntries(
        entry("aws-query-protocol", List.of(patchInfo("software/amazon/awssdk/protocols/query/internal/marshall/ListQueryMarshaller.class",
            "213e84d9a745bdae4b844334d17aecdd6499b36df32aa73f82dc114b35043009",
            AwsSdkStringFormatInPathResolverPatcher::new))),
        entry("hadoop-common-2(?!.*tests)",
            List.of(
                patchInfo(
                    "org/apache/hadoop/util/ShutdownHookManager.class",
                    "3912451f02da9199dae7dba3f1420e0d951067addabbb235e7551de52234a0ef",
                    HdfsShutdownHookManagerPatcher::new
                ),
                patchInfo(
                    "org/apache/hadoop/util/Shell.class",
                    "60400dc800e7c3e1a5fc499793033d877f5319bbd7633fee05d5a1d96b947bbd",
                    HdfsShellPatcher::new
                ),
                patchInfo(
                    "org/apache/hadoop/security/UserGroupInformation.class",
                    "218078b8c77838f93d015c843775985a71f3c7a8128e2a9394410f0cd1da5f53",
                    HdfsSubjectGetSubjectPatcher::new
                )
            )),
        entry("hadoop-common-3(?!.*tests)", List.of(
            patchInfo(
                "org/apache/hadoop/util/ShutdownHookManager.class",
                "7720e8545a02de6fd03f4170f0e471d1301ef73d7d6a09097bad361f9e31f819",
                HdfsShutdownHookManagerPatcher::new
            ),
            patchInfo(
                "org/apache/hadoop/util/Shell.class",
                "856d0b829cf550df826387af15fa1c772bc7d26d6461535b17b9d5114d308dc4",
                HdfsShellPatcher::new
            ),
            patchInfo(
                "org/apache/hadoop/security/UserGroupInformation.class",
                "52f5973f35a282908d48a573a03c04f240a22c9f6007d7c5e7852aff1c641420",
                HdfsSubjectGetSubjectPatcher::new
            )
        )),
        entry("hadoop-client-api", List.of(
            patchInfo(
                "org/apache/hadoop/util/ShutdownHookManager.class",
                "90641e0726fc9372479728ef9b7ae2be20fb7ab4cddd4938e55ffecadddd4d94",
                HdfsShutdownHookManagerPatcher::new
            ),
            patchInfo(
                "org/apache/hadoop/util/Shell.class",
                "8837c7f3eeda3f658fc3d6595f18e77a4558220ff0becdf3e175fa4397a6fd0c",
                HdfsShellPatcher::new
            ),
            patchInfo(
                "org/apache/hadoop/security/UserGroupInformation.class",
                "3c34bbc2716a6c8f4e356e78550599b0a4f01882712b4f7787d032fb10527212",
                HdfsSubjectGetSubjectPatcher::new
            ),
            patchInfo(
                "org/apache/hadoop/security/authentication/client/KerberosAuthenticator.class",
                "6bab26c1032a38621c20050ec92067226d1d67972d0d370e412ca25f1df96b76",
                HdfsSubjectGetSubjectPatcher::new
            )
        ))
    );

    private static PatchInfo patchInfo(String jarEntryName, String classSha256, Function<ClassWriter, ClassVisitor> patcherFactory) {
        return new PatchInfo(jarEntryName, HexFormat.of().parseHex(classSha256), patcherFactory);
    }

    @Override
    public void apply(Project project) {
        var patched = Attribute.of("patched", Boolean.class);

        project.getConfigurations().configureEach(configuration -> {
            configuration.getAttributes().attribute(patched, true);
        });

        var deps = project.getDependencies();
        deps.getAttributesSchema().attribute(patched);
        deps.getArtifactTypes().getByName("jar").getAttributes().attribute(patched, false);
        for (var patch : patches.entrySet()) {
            deps.registerTransform(DependencyPatchTransform.class, spec -> {
                spec.getFrom().attribute(patched, false);
                spec.getTo().attribute(patched, true);
                spec.parameters(params -> {
                    params.getJarPattern().set(patch.getKey());
                    params.getPatches().set(patch.getValue());
                });
            });
        }

    }
}

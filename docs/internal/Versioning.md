# Versioning in Elasticsearch

Elasticsearch is a complex product used in many scenarios. A single version
number is not enough to cover all aspects of the product. Instead, different
versioning concepts are used that depend on scope, updatability,
responsiveness, and maintenance needs.

## Release Version

This is the version number for published releases of Elasticsearch and the
Elastic Stack. It uses the format _major.minor.patch_, plus a corresponding
version id.

Avoid using this version number in code, since it does not apply to all cases
and may break Elasticsearch nodes.

You can access the release version in code via `Build.current().version()`,
but do **not** assume it is a semantic version number—it may be any arbitrary
string.

## Transport Protocol

The transport protocol transfers binary data between Elasticsearch nodes.
`TransportVersion` encapsulates versioning for this protocol. When two nodes
connect, the highest shared version is selected. You can access this version
with the `getTransportVersion` method in `StreamInput` and `StreamOutput`, so
serialization code can read and write objects in a format understood by the
other node.

A `TransportVersion` has one id per release branch it will be committed to,
with a name chosen when generated. To ensure consistency, all new
`TransportVersion`s must be created in the `main` branch and then backported
to the relevant release branches.

### Internal State Files

The Elasticsearch server jar contains resource files for each transport
version. These files are loaded at runtime to construct `TransportVersion`
instances. Each version has its own file, allowing backports without
conflict.

Additional resource files represent the latest transport version for each
release branch. If two transport versions are added at the same time, there
will be a conflict in these state files, requiring one to be regenerated
before merging to `main`.

All internal state files are managed by Gradle tasks and should not be edited
directly.

_Elastic developers_: See the Serverless documentation about creating
transport versions for Serverless changes.

### Creating Transport Versions Locally

To create a transport version, declare a reference in Java code. For example:

```java
private static final TransportVersion MY_NEW_TV =
    TransportVersion.fromName("my_new_tv");
```

`fromName` takes a String literal matching the regex `[_0-9a-zA-Z]+`. You can
reference the same name from multiple classes but must not reuse an existing
name after it is committed to `main`.

Once declared, use your `TransportVersion` in serialization code. For example,
in a constructor with `StreamInput in`:

```java
if (in.getTransportVersion().supports(MY_NEW_TV)) {
    // new serialization code
}
```

To run Elasticsearch or tests, you must generate transport version ids.
Run:

```
./gradlew generateTransportVersion
```

To backport your code, include target branches:

```
./gradlew generateTransportVersion --backport-branches=9.1,8.19
```

### Updating Transport Versions

You can modify a transport version before merging to `main`, including
renaming, updating branches, or removing the version.

The generation task is idempotent and can be rerun anytime for a valid
internal state. To add a backport branch, rerun with all target branches:

```
./gradlew generateTransportVersion --backport-branches=9.1,9.0,8.19,8.18
```

CI can also update transport versions automatically. As version labels are
updated on your PR, the generation task runs with the correct backport
branches and commits internal state changes to your branch.

You can add branches after merging to `main`. For example, if you committed
`my_tv` to `main` and `9.1`, but need to backport to `8.19`, run (in `main`):

```
./gradlew generateTransportVersion --name=my_tv --backport-branches=9.1,8.19
```

CI will not know what name to update, so you must run the generate task as
shown. After merging, backport the updated version to all applicable
branches.

### Resolving Merge Conflicts

Transport versions are created sequentially. If two developers create a
version at the same time from the same `main` commit, they will generate the
same internal ids. The first merged into `main` wins; the second has a merge
conflict.

To resolve, merge `main` into your branch. You will have conflicts with
transport version internal state files. Run:

```
./gradlew generateTransportVersion --resolve-conflict
```

This command regenerates your transport version and stages updated state
files in git. You can then finish the merge.

### Reverting Changes

Transport versions cannot be removed, only added. If logic using a transport
version must be reverted, do so with a new transport version.

For example, if you added `original_tv` and now want to revert it, add
`revert_tv`:

```java
TransportVersion tv = in.getTransportVersion();
if (tv.supports(ORIGINAL_TV) && !tv.supports(REVERT_TV)) {
    // serialization code being reverted
}
```

### Minimum Compatibility Versions

The transport version used between nodes is set by the initial handshake (see
`TransportHandshaker`). The minimum compatible transport version for the node
is set by `TransportVersion.minimumCompatible()`. Nodes below this version
cannot join the cluster. Update this constant manually during major releases.

The minimum version for CCS is set by `TransportVersion.minimumCCSVersion()`.
This is not checked before queries, but if a query cannot be serialized at
that version, it is rejected. This constant is updated automatically as part
of releases.

### Mapping to Release Versions

Log and exception messages may reference arbitrary transport versions. To map
these to release versions, use `.toReleaseVersion()`, which relies on metadata
in `TransportVersions.csv`. For unmapped versions, a best guess is used. The
CSV file is updated automatically during releases.

For releases without a release version number, `.toReleaseVersion()` does
nothing.

## Index Version

Index version is a single incrementing number for the index data format,
metadata, and mappings. It is declared similarly to transport version, using
the pattern `M_NNN_S_PP`.

Index version is stored in index metadata at creation and determines the
storage format and supported functionality. Once set, it does not change.

For changes to the index format, metadata, or mappings, create a new constant
below the last one, incrementing the `NNN` component.

Unlike transport versions, index version constants cannot be collapsed, as
each index retains its creation version. Fortunately, new index versions are
created infrequently, so the list is manageable.

Index version also has a `toReleaseVersion` method for mapping to release
versions when relevant.

## Cluster Features

Cluster features are identifiers published by nodes in cluster state to signal
support for particular operations or functionalities. They are used for
internal checks and for gating tests on certain features, such as verifying
all nodes have upgraded before migrations. **Cluster features should not be
used outside the Elasticsearch codebase.**

Cluster features indicate top-level functionality, such as a new transport
endpoint or operation.

They also check if nodes can join a cluster. Once all nodes support a
feature, no new node can join without supporting it. This ensures features
remain supported in the future.

To declare a new cluster feature, add an implementation of the
`FeatureSpecification` SPI, register it, and add the feature as a constant
returned by `getFeatures`. To check whether all nodes support a feature, use
`clusterHasFeature` on `FeatureService`. Checking individual nodes is not
supported.

Once deployed, cluster features cannot be modified or removed. Otherwise, new
nodes may not join existing clusters. If a feature needs removal, add a new
feature indicating the functionality is no longer supported, and update code
accordingly, keeping backward compatibility in mind.

The infrastructure is designed for a few hundred features per major release.
Since features cannot be removed, use them sparingly to avoid instability.

When releasing a new major version _N_, backward compatibility is limited to
the highest minor of the previous major (_N-1_). Clusters with the new major
version are guaranteed to have all features from the previous major, so these
checks can be removed over time.

### Testing

Tests often verify feature availability across all nodes, especially in
backward compatibility or mixed-cluster scenarios. Instead of adding a
production feature for tests, add a _test feature_ via
`FeatureSpecification.getTestFeatures`, which is only set in integration test
clusters. Prefer Capabilities for test conditions when possible.

In Java REST tests, use `ESRestTestCase.clusterHasFeature(feature)`.

In YAML REST tests, specify conditions in `requires` or `skip` sections using
cluster features. See [the documentation](https://github.com/elastic/elasticsearch/blob/main/rest-api-spec/src/yamlRestTest/resources/rest-api-spec/test/README.asciidoc#skipping-tests) for details.

For backward compatibility tests, the framework adds synthetic features for
each previously released Elasticsearch version, named `gte_v{VERSION}`
(e.g., `gte_v8.14.2`). These can be used for conditions based on previous
releases, but **not** for checking the current snapshot version—use real
features or capabilities instead.

## Capabilities

The Capabilities API is a REST endpoint that lets external clients check the
capabilities of an Elasticsearch cluster. It is dynamically calculated for
each query, so there is no limit on size or usage.

A capabilities query can answer:

* Is this endpoint supported for this HTTP method?
* Are these parameters for this endpoint supported?
* Are these capabilities (arbitrary string IDs) for this endpoint supported?

The API returns `true` if all specified aspects are supported by all nodes.
If any aspect is unsupported by any node, it returns `false`. If there are
communication problems, the response is `supported: null`.

All registered endpoints automatically support existence checks. To add
parameter and feature capability queries to your REST endpoint, implement
`supportedQueryParameters` and `supportedCapabilities` in your handler.

To perform a capability query, send a REST call to the `_capabilities` API
with parameters: `method`, `path`, `parameters`, and `capabilities`. The call
checks every node and returns `{supported: true}` only if all nodes support
the specified combination; otherwise, it returns `{supported: false}` or
`{supported: null}` if support cannot be determined. Use
`ESRestTestCase.clusterHasCapability` to check capabilities in tests.

YAML tests can specify skip and requires conditions with capabilities, for
example:

```yaml
- requires:
    capabilities:
      - method: GET
        path: /_endpoint
        parameters: [param1, param2]
        capabilities: [cap1, cap2]
```

Note: `method: GET` is the default and does not need to be specified.

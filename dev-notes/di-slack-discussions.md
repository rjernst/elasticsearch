# Dependency Injection Slack Discussion Summary

## Proxy Performance Benchmarking
- @patrick.doyle benchmarked different proxy implementations; the invokedynamic-based proxy was fastest at 2719M interface method calls per second.
- It matched the performance of using a final field directly, indicating zero overhead.
- Performance was unchanged between 1 and 3 iterations, indicating it plays nicely with the JIT.

## Proxy Factory Implementation Issues
- @patrick.doyle hit a `ServiceConfigurationError` when trying to instantiate `ProxyFactoryImpl` — the public no-arg constructor wasn't accessible.
- @ryan suggested it was related to security permissions (see #93714) and provided a code snippet using `AccessController.doPrivileged` as a workaround.

## Injecting Collections and Suppliers
- @patrick.doyle did prep work to enable injecting `Collection<T>` and `Supplier<T>`.
- @ryan pushed back: generic types like `Collection` should be replaced with more specific classes, and `Supplier` should only be used for circular dependencies (which should be fixed).

## Gradual Migration to the New Injector
- @patrick.doyle proposed starting by injecting `RestHandler` instances, then gradually expanding.
- `NodeConstruction.construct` would use a mix of manual object creation, injector-created objects, and injector proxies, gradually reducing manual creation over time.

## Removing ActionType and Strongly Typed ActionRequest
- @ryan plans to remove the `ActionType` concept and look up `TransportAction` instances based on the `ActionRequest` class.
- A temporary `LegacyActionRequest` interface enables incremental conversion of existing `ActionRequest` implementations.

## Terminology and Principles
- @ryan proposed using "bundles" instead of "modules" or "plugins" for collections of components.
- @patrick.doyle and @ryan discussed distinctions between "plugins", "bundles", and "components", and how the injector should handle listener registration.
- @patrick.doyle raised concerns with Guice-style child injectors not supporting the listener pattern well.

## Integrating with Existing Initialization Code
- @patrick.doyle proposed merging several injector features to support coexistence with existing init code: proxies, factory methods, and multiple calls to `inject`.

## Removing Guice
- Plan is to gradually remove Guice starting with `RestHandler` injection, then expanding.
- @ryan noted the new injector needs better error messages to help identify what was asking for a particular dependency.

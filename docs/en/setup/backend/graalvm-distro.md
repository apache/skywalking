# GraalVM Native Image Distribution

Apache SkyWalking provides an alternative distribution built with GraalVM native image, delivered as a
self-contained binary with faster startup and lower memory footprint.

## Why Choose the GraalVM Distro

| Aspect                               | Standard OAP (JVM)                         | GraalVM Distro             |
|--------------------------------------|--------------------------------------------|----------------------------|
| Startup                              | Seconds to minutes                         | Instant                    |
| Memory                               | 1 GB+ heap typical                         | ~512 MB                    |
| Artifact                             | JARs + JVM required                        | ~203 MB single binary      |
| Storage backends                     | BanyanDB, Elasticsearch, MySQL, PostgreSQL | BanyanDB only              |
| Module set                           | Dynamically loaded via SPI                 | Fixed at build time        |
| DSL rules (OAL, MAL, LAL, Hierarchy) | Compiled at startup                        | Pre-compiled at build time |

Benchmark results (Apple M3 Max, BanyanDB backend, 20 RPS sustained load):

| Metric               | Standard OAP (JVM) | GraalVM Distro | Improvement   |
|----------------------|--------------------|----------------|---------------|
| Cold boot            | 635 ms             | 5 ms           | ~127x faster  |
| Memory (idle)        | ~1.2 GiB           | ~41 MiB        | 97% reduction |
| Memory (20 RPS)      | 2,068 MiB          | 629 MiB        | 70% reduction |
| CPU (median, 20 RPS) | 101 millicores     | 68 millicores  | 33% reduction |
| Throughput           | Baseline           | Identical      | No difference |

See the [full benchmark blog post](https://skywalking.apache.org/blog/2026-03-13-skywalking-graalvm-distro-design-and-benchmarks/) for details.

The GraalVM distro is a good fit when you want:

- **Container-friendly deployments** with minimal image size and instant readiness.
- **Lower resource usage** for small-to-medium scale environments.
- **BanyanDB as your storage backend**, which is already the recommended default.

## Limitations

- **BanyanDB is the sole supported storage backend.** Elasticsearch, MySQL, and PostgreSQL are not available.
- **Modules are selected at build time.** Runtime SPI discovery is not supported.
- **DSL rules are pre-compiled at build time.** Dynamic rule changes require rebuilding the binary.

All existing SkyWalking agents, UI, and CLI tools remain fully compatible.

## Download

Pre-built binaries are available for Linux (AMD64/ARM64) and macOS (ARM64) on the
[SkyWalking Downloads](https://skywalking.apache.org/downloads/#SkyWalkingGraalVMDistro) page.

Docker images are also available on GHCR.

## Version Mapping

Each GraalVM distro release corresponds to a specific SkyWalking OAP version.
See the [version mapping table](https://skywalking.apache.org/docs/skywalking-graalvm-distro/next/version-mapping/)
for the exact correspondence.

## Documentation

For setup instructions, configuration details, and the full documentation, refer to the
[SkyWalking GraalVM Distro documentation](https://skywalking.apache.org/docs/skywalking-graalvm-distro/latest/readme/).

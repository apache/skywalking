## 10.4.0

#### Project
* Introduce OAL V2 engine:
  - Immutable AST models for thread safety and predictable behavior
  - Type-safe enums replacing string-based filter operators
  - Precise error location reporting with file, line, and column numbers
  - Clean separation between parsing and code generation phases
  - Enhanced testability with models that can be constructed without parsing
* Fix E2E test metrics verify: make it failure if the metric values all null.
* Support building, testing, and publishing with Java 25.
* Add `CLAUDE.md` as AI assistant guide for the project.
* Upgrade Groovy to 5.0.3 in OAP backend.
* Bump up nodejs to v24.13.0 for the latest UI(booster-ui) compiling.
* Add `library-batch-queue` module — a partitioned, self-draining queue with type-based dispatch,
  adaptive partitioning, idle backoff, and throughput-weighted drain rebalancing (`DrainBalancer`).
  Designed to replace DataCarrier in high-fan-out scenarios.
* Replace DataCarrier with BatchQueue for L1 metrics aggregation, L2 metrics persistence, TopN persistence,
  all three exporters (gRPC metrics, Kafka trace, Kafka log), and gRPC remote client.
  All metric types (OAL + MAL) now share unified queues instead of separate OAL/MAL pools.
  Each exporter keeps its own dedicated queue with 1 thread, preserving original buffer strategies.
  Thread count comparison on an 8-core machine (gRPC remote client excluded — unchanged 1 thread per peer):

  | Queue | Old threads | Old channels | Old buffer slots | New threads | New partitions | New buffer slots | New policy |
  |-------|-------------|--------------|------------------|-------------|----------------|------------------|------------|
  | L1 Aggregation (OAL) | 24 | ~1,240 | ~12.4M | 8 (unified) | ~330 adaptive | ~6.6M | `cpuCores(1.0)` |
  | L1 Aggregation (MAL) | 2 | ~100 | ~100K | (unified above) | | | |
  | L2 Persistence (OAL) | 2 | ~620 | ~1.24M | 3 (unified) | ~330 adaptive | ~660K | `cpuCoresWithBase(1, 0.25)` |
  | L2 Persistence (MAL) | 1 | ~100 | ~100K | (unified above) | | | |
  | TopN Persistence | 4 | 4 | 4K | 1 | 4 adaptive | 4K | `fixed(1)` |
  | Exporters (gRPC/Kafka) | 3 | 6 | 120K | 3 (1 per exporter) | — | 60K | `fixed(1)` each |
  | **Total** | **36** | **~2,070** | **~13.9M** | **15** | **~664** | **~7.3M** | |

* Remove `library-datacarrier-queue` module. All usages have been replaced by `library-batch-queue`.
* Enable throughput-weighted drain rebalancing for L1 aggregation and L2 persistence queues (10s interval).
  Periodically reassigns partitions across drain threads to equalize load when metric types have skewed throughput.
* Add benchmark framework under `benchmarks/` with Kind-based Kubernetes environments, automated thread dump
  collection and analysis. First case: `thread-analysis` on `istio-cluster_oap-banyandb` environment.
* Add virtual thread support (JDK 25+) for gRPC and Armeria HTTP server handler threads.
  Set `SW_VIRTUAL_THREADS_ENABLED=false` to disable.

  | Pool | Threads (JDK < 25) | Threads (JDK 25+) |
  |---|---|---|
  | gRPC server handler (`core-grpc`, `receiver-grpc`, `als-grpc`, `ebpf-grpc`) | Cached platform (unbounded) | Virtual threads |
  | HTTP blocking (`core-http`, `receiver-http`, `promql-http`, `logql-http`, `zipkin-query-http`, `zipkin-http`, `firehose-http`) | Cached platform (max 200) | Virtual threads |
  | VT carrier threads (ForkJoinPool) | N/A | ~9 shared |

  On JDK 25+, all 11 thread pools above share ~9 carrier threads instead of up to 1,400+ platform threads.
* Change default Docker base image to JDK 25 (`eclipse-temurin:25-jre`). JDK 11 kept as `-java11` variant.
* Thread count benchmark comparison — 2-node OAP cluster on JDK 25 with BanyanDB, Istio bookinfo traffic
  (10-core machine, JVM-internal threads excluded):

  | Pool                                  | v10.3.0 threads    | v10.4.0 threads | Notes                                       |
  |---------------------------------------|--------------------|-----------------|---------------------------------------------|
  | L1 Aggregation (OAL + MAL)            | 26 (DataCarrier)   | 10 (BatchQueue) | Unified OAL + MAL                           |
  | L2 Persistence (OAL + MAL)            | 3 (DataCarrier)    | 4 (BatchQueue) | Unified OAL + MAL                           |
  | TopN Persistence                      | 4 (DataCarrier)    | 1 (BatchQueue) |                                             |
  | gRPC Remote Client                    | 1 (DataCarrier)    | 1 (BatchQueue) | Per peer                                    |
  | Armeria HTTP event loop               | 20                 | 5 | `min(5, cores)` shared group                |
  | Armeria HTTP handler                  | on-demand platform(increasing with payload) | - | Virtual threads on JDK 25+                  |
  | gRPC event loop                       | 10                 | 10 | Unchanged                                   |
  | gRPC handler                          | on-demand platform(increasing with payload)| - | Virtual threads on JDK 25+                  |
  | ForkJoinPool (Virtual Thread carrier) | 0                  | ~10 | JDK 25+ virtual thread scheduler            |
  | HttpClient-SelectorManager            | 4                  | 2 | SharedKubernetesClient                      |
  | Schedulers + others                   | ~24                | ~24 | Mostly unchanged                            |
  | **Total (OAP threads)**               | **150+**           | **~72** | **~50% reduction, stable in high payload.** |

* Replace PowerMock Whitebox with standard Java Reflection in `server-library`, `server-core`, and `server-configuration` to support JDK 25+.

#### OAP Server

* KubernetesCoordinator: make self instance return real pod IP address instead of `127.0.0.1`.
* Enhance the alarm kernel with recovered status notification capability
* Fix BrowserWebVitalsPerfData `clsTime` to `cls` and make it double type.
* Init `log-mal-rules` at module provider start stage to avoid re-init for every LAL.
* Fail fast if SampleFamily is empty after MAL filter expression.
* Fix range matrix and scalar binary operation in PromQL.
* Add `LatestLabeledFunction` for meter.
* MAL Labeled metrics support additional attributes.
* Bump up netty to 4.2.9.Final.
* Add support for OpenSearch/ElasticSearch client certificate authentication.
* Fix BanyanDB logs paging query.
* Replace BanyanDB Java client with native implementation.
* Remove `bydb.dependencies.properties` and set the compatible BanyanDB API version number in `${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS}`.
* Fix trace profiling query time range condition.
* Add named ThreadFactory to all `Executors.newXxx()` calls to replace anonymous `pool-N-thread-M` thread names
  with meaningful names for easier thread dump analysis. Complete OAP server thread inventory
  (counts on an 8-core machine, exporters and JDBC are optional):

  | Catalog | Thread Name | Count | Policy | Partitions |
  |---------|-------------|-------|--------|------------|
  | Data Pipeline | `BatchQueue-METRICS_L1_AGGREGATION-N` | 8 | `cpuCores(1.0)` | ~330 adaptive |
  | Data Pipeline | `BatchQueue-METRICS_L2_PERSISTENCE-N` | 3 | `cpuCoresWithBase(1, 0.25)` | ~330 adaptive |
  | Data Pipeline | `BatchQueue-TOPN_PERSISTENCE-N` | 1 | `fixed(1)` | ~4 adaptive |
  | Data Pipeline | `BatchQueue-GRPC_REMOTE_{host}_{port}-N` | 1 per peer | `fixed(1)` | `fixed(1)` |
  | Data Pipeline | `BatchQueue-EXPORTER_GRPC_METRICS-N` | 1 | `fixed(1)` | `fixed(1)` |
  | Data Pipeline | `BatchQueue-EXPORTER_KAFKA_TRACE-N` | 1 | `fixed(1)` | `fixed(1)` |
  | Data Pipeline | `BatchQueue-EXPORTER_KAFKA_LOG-N` | 1 | `fixed(1)` | `fixed(1)` |
  | Data Pipeline | `BatchQueue-JDBC_ASYNC_BATCH_PERSISTENT-N` | 4 (configurable) | `fixed(N)` | `fixed(N)` |
  | Scheduler | `RemoteClientManager` | 1 | scheduled | — |
  | Scheduler | `PersistenceTimer` | 1 | scheduled | — |
  | Scheduler | `PersistenceTimer-prepare-N` | 2 (configurable) | fixed pool | — |
  | Scheduler | `DataTTLKeeper` | 1 | scheduled | — |
  | Scheduler | `CacheUpdateTimer` | 1 | scheduled | — |
  | Scheduler | `HierarchyAutoMatching` | 1 | scheduled | — |
  | Scheduler | `WatermarkWatcher` | 1 | scheduled | — |
  | Scheduler | `AlarmCore` | 1 | scheduled | — |
  | Scheduler | `HealthChecker` | 1 | scheduled | — |
  | Scheduler | `EndpointUriRecognition` | 1 (conditional) | scheduled | — |
  | Scheduler | `FileChangeMonitor` | 1 | scheduled | — |
  | Scheduler | `BanyanDB-ChannelManager` | 1 | scheduled | — |
  | Scheduler | `GRPCClient-HealthCheck-{host}:{port}` | 1 per client | scheduled | — |
  | Scheduler | `EBPFProfiling-N` | configurable | fixed pool | — |
* Fix BanyanDB time range overflow in profile thread snapshot query.
* `BrowserErrorLog`, OAP Server generated UUID to replace the original client side ID, because Browser scripts can't guarantee generated IDs are globally unique.
* MQE: fix multiple labeled metric query and ensure no results are returned if no label value combinations match.
* Fix `BrowserErrorLog` BanyanDB storage query order.
* `BanyanDB Client`: Property query support `Order By`.
* MQE: trim the label values condition for the labeled metrics query to enhance the readability.
* PromQL service: fix time parse issue when using RFC3339 time format for querying.
* Envoy metrics service receiver: support adapter listener metrics.
* Envoy metrics service receiver: support config MAL rules files.
* Fix `HttpAlarmCallback` creating a new `HttpClient` on every alarm `post()` call, leaking NIO selector threads.
  Replace with a shared static singleton.
* Add `SharedKubernetesClient` singleton in `library-kubernetes-support` to replace 9 separate
  `KubernetesClientBuilder().build()` calls across 7 files. Fixes `KubernetesCoordinator` client leak
  (never closed, NIO selector thread persisted). Uses `KubernetesHttpClientFactory` with virtual threads
  on JDK 25+ or a single fixed executor thread on JDK <25.
* Reduce Armeria HTTP server event loop threads. All 7 HTTP servers now share one event loop group
  instead of each creating their own (Armeria default: `cores * 2` per server = 140 on 10-core).
  Event loop: `min(5, cores)` shared — non-blocking I/O multiplexing needs few threads.
  Blocking executor: JDK 25+ uses virtual threads; JDK <25 keeps Armeria's default cached pool
  (up to 200 on-demand threads) because HTTP handlers block on long storage/DB queries.

#### UI
* Fix the missing icon in new native trace view.
* Enhance the alert page to show the recovery time of resolved alerts.
* Implement a common pagination component.
* Fix validation guard for router.
* Add the `coldStage` to the `Duration` for queries.
* Optimize the pages theme.

#### Documentation

* Add benchmark selection into banyanDB storage documentation.
* Fix progressive TTL doc for banyanDB.
* Restructure `docs/README.md` for better navigation with high-level documentation overview.
* Move Marketplace as a top-level menu section with Overview introduction in `menu.yml`.
* Polish `marketplace.md` as the overview page for all out-of-box monitoring features.
* Add "What's Next" section to Quick Start docs guiding users to Marketplace.
* Restructure agent compatibility page with OAP 10.x focus and clearer format for legacy versions.
* Remove outdated FAQ docs (v3, v6 upgrade guides and 7.x metrics issue).
* Remove "since 7/8/9.x" version statements from documentation as features are standard in 10.x.


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.4.0)


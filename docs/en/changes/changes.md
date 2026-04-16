## 10.5.0

#### Project
* Bump infra-e2e to testcontainers-go v0.42.0 (apache/skywalking-infra-e2e#146), which uses Docker Compose v2 plugin natively and removes docker-compose v1 dependency.
* Remove deprecated `version` field from all docker-compose files for Compose v2 compatibility.

#### OAP Server
* Add Zipkin Virtual GenAI e2e test. Use `zipkin_json` exporter to avoid protobuf dependency conflict
  between `opentelemetry-exporter-zipkin-proto-http` (protobuf~=3.12) and `opentelemetry-proto` (protobuf>=5.0).
* Fix missing `taskId` filter and incorrect `IN` clause parameter binding in `JDBCJFRDataQueryDAO` and `JDBCPprofDataQueryDAO`.
* Remove deprecated `GroupBy.field_name` from BanyanDB `MeasureQuery` request building (Phase 1 of staged removal across repos).
* Push `taskId` filter down to the storage layer in `IAsyncProfilerTaskLogQueryDAO`, removing in-memory filtering from `AsyncProfilerQueryService`.
* Fix missing parentheses around OR conditions in `JDBCZipkinQueryDAO.getTraces()`, which caused the table filter to be bypassed for all but the first trace ID. Replaced with a proper `IN` clause.
* Fix missing `and` keyword in `JDBCEBPFProfilingTaskDAO.getTaskRecord()` SQL query, which caused a syntax error on every invocation.
* Fix storage layer bugs in profiling DAOs and add unit test coverage for JDBC query DAOs.
  Bug fixes: duplicate `TABLE_COLUMN` condition in `JDBCMetadataQueryDAO.findEndpoint()`,
  wrong merged table check in `JFRDataQueryEsDAO` (used incorrect INDEX_NAME due to copy-paste),
  and missing `isMergedTable` check in `ProfileTaskQueryEsDAO.getById()`.
  Test additions: add unit tests for 21 JDBC query DAOs verifying SQL/WHERE clause construction.
* Support MCP (Model Context Protocol) observability for Envoy AI Gateway: MCP metrics (request CPM/latency, method breakdown, backend breakdown, initialization latency, capabilities), MCP access log sampling (errors only), `ai_route_type` searchable log tag, and MCP dashboard tabs.
* Add weighted handler support to `BatchQueue` adaptive partitioning. MAL metrics use weight 0.05 at L1 (vs 1.0 for OAL), reducing partition count and memory overhead when many MAL metric types are registered.
* Fix missing `taskId` filter in pprof task log query and its JDBC/BanyanDB/Elasticsearch implementations.
* Fix duplicate calls in `EndpointTopologyBuilder` — calls were not deduplicated unlike `ServiceTopologyBuilder`, causing duplicate entries when storage returns multiple records for the same relation.
* Use `containsOnce` and `noDuplicates` for topology dependency e2e expected files to enforce no-duplicate verification.
* Bump infra-e2e to `ef073ad` to include `noDuplicates` pipe function support.
* PromQL: support querying Zipkin metadata (service name, remote service name, span name).
* TraceQL: support more tags and variables in Grafana for querying.
* LAL: add `sourceAttribute()` function for non-persistent OTLP resource attribute access in LAL scripts.
* LAL: add `layer: auto` mode for dynamic layer assignment when `service.layer` is absent.
* Add two-phase `SpanListener` SPI mechanism for extensible trace span processing. Refactor GenAI from hardcoded `SpanForward.processGenAILogic()` to `GenAISpanListener`.
* Add OTLP/HTTP receiver support for traces, logs, and metrics (`/v1/traces`, `/v1/logs`, `/v1/metrics`). Supports both `application/x-protobuf` and `application/json` content types.

#### UI

#### Documentation
* Update LAL documentation with `sourceAttribute()` function and `layer: auto` mode.

All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.5.0)


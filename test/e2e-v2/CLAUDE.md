# E2E-V2 Test Framework Guide

## Overview

SkyWalking uses [skywalking-infra-e2e](https://github.com/apache/skywalking-infra-e2e) for end-to-end testing. Tests follow a **Setup → Trigger → Verify → Cleanup** lifecycle, with expected files using Go templates for flexible result matching.

## Directory Structure

```
test/e2e-v2/
├── cases/                          # Test scenarios (one dir per feature)
│   ├── <feature>/
│   │   ├── e2e.yaml                # Main config: setup, trigger, verify, cleanup
│   │   ├── <feature>-cases.yaml    # Verify cases: query + expected pairs
│   │   ├── expected/               # Expected result files (Go templates)
│   │   │   └── *.yml
│   │   ├── docker-compose.yml      # Infrastructure for Docker-based tests
│   │   └── <storage>/              # Storage-specific variants (es/, banyandb/, etc.)
│   │       ├── docker-compose.yml
│   │       └── e2e.yaml
│   └── ...
├── script/                         # Shared scripts and environment setup
│   ├── env                         # Environment variables (SkyWalking version, etc.)
│   └── prepare/                    # Setup scripts (yq, swctl installation)
└── java-test-service/              # Java test service implementations
```

## Key Files to Find

| What you need | Where to look |
|---------------|---------------|
| Test entry point (config) | `cases/<feature>/e2e.yaml` or `cases/<feature>/<storage>/e2e.yaml` |
| Query + expected pairs | `cases/<feature>/*-cases.yaml` (referenced from e2e.yaml `verify.cases.includes`) |
| Expected result templates | `cases/<feature>/expected/*.yml` |
| Docker infrastructure | `cases/<feature>/docker-compose.yml` or `cases/<feature>/<storage>/docker-compose.yml` |
| Shared env variables | `script/env` |
| Test services source | `java-test-service/` |

## Test Cases by Feature Area

| Directory | Protocol | Description |
|-----------|----------|-------------|
| `simple/` | GraphQL (swctl) | Core service/endpoint/trace/metrics verification |
| `mqe/` | GraphQL (swctl) | Metrics Query Engine expression tests |
| `alarm/` | GraphQL (swctl) | Alarm rule verification |
| `logql/` | LogQL (curl) | Loki-compatible log query API |
| `promql/` | PromQL (curl) | Prometheus-compatible metrics API |
| `traceql/` | TraceQL (curl) | TraceQL search API via Zipkin endpoint |
| `zipkin/` | Zipkin v2 (curl) | Native Zipkin trace API |
| `log/` | GraphQL (swctl) | Log collection and analysis |
| `meter/` | GraphQL (swctl) | Meter/MAL metrics |
| `profiling/` | GraphQL (swctl) | Profiling (CPU, memory, network, eBPF) |
| `browser/` | GraphQL (swctl) | Browser/RUM monitoring |
| `event/` | GraphQL (swctl) | Event collection |
| `baseline/` | GraphQL (swctl) | Baseline metrics |

## Query Protocols & Ports

| Protocol | Port Variable | Base URL | Query Tool | Spec Doc |
|----------|---------------|----------|------------|----------|
| GraphQL / MQE | `${oap_12800}` | `http://${oap_host}:${oap_12800}/graphql` | `swctl` CLI | [graphql](e2e-expectation-graphql.md) |
| Status / Debug | `${oap_12800}` | `http://${oap_host}:${oap_12800}/debugging/` | `curl` | [status-debug](e2e-expectation-status-debug.md) |
| PromQL | `${oap_9090}` | `http://${oap_host}:${oap_9090}/api/v1/` | `curl` | [promql](e2e-expectation-promql.md) |
| LogQL | `${oap_3100}` | `http://${oap_host}:${oap_3100}/loki/api/v1/` | `curl` | [logql](e2e-expectation-logql.md) |
| TraceQL | `${oap_3200}` | `http://${oap_host}:${oap_3200}/zipkin/api/` | `curl` | [traceql](e2e-expectation-traceql-zipkin.md) |
| Zipkin v2 | `${oap_9412}` | `http://${oap_host}:${oap_9412}/zipkin/api/v2/` | `curl` | [zipkin](e2e-expectation-zipkin.md) |

## GraphQL Schema Definitions

All GraphQL types and queries are defined in `.graphqls` files at:
```
oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol/
```
Key schemas: `common.graphqls` (shared types), `metadata-v2.graphqls` (services/instances/endpoints), `topology.graphqls` (dependency graphs), `trace.graphqls` + `trace-v2.graphqls` (distributed traces), `metrics-v3.graphqls` (MQE), `alarm.graphqls`, `log.graphqls`, `event.graphqls`, `hierarchy.graphqls`. See [e2e-expectation-graphql.md](e2e-expectation-graphql.md) for the full schema-to-expected-file mapping.

## How Expected Files Work

Expected files are **Go templates** that render with actual query results as context. After rendering, the framework compares the rendered YAML against the actual YAML using structural equality (`go-cmp`).

### Expectation specification documents

For detailed template syntax and protocol-specific patterns, see:
- [e2e-expectation-spec.md](e2e-expectation-spec.md) — Core template syntax and functions
- [e2e-expectation-graphql.md](e2e-expectation-graphql.md) — GraphQL/MQE expected file patterns (with `.graphqls` schema references)
- [e2e-expectation-logql.md](e2e-expectation-logql.md) — LogQL expected file patterns
- [e2e-expectation-promql.md](e2e-expectation-promql.md) — PromQL expected file patterns
- [e2e-expectation-traceql-zipkin.md](e2e-expectation-traceql-zipkin.md) — TraceQL (Tempo-compatible) expected file patterns
- [e2e-expectation-zipkin.md](e2e-expectation-zipkin.md) — Zipkin v2 native API expected file patterns
- [e2e-expectation-status-debug.md](e2e-expectation-status-debug.md) — Status & debugging HTTP endpoint patterns

## Common Patterns

### Adding a new e2e test case
1. Add query + expected pair in the `*-cases.yaml` file
2. Create expected file in `expected/` directory using Go template syntax
3. Use `contains` for unordered list matching, `notEmpty` for dynamic values, `b64enc` for IDs

### Storage variants
Many features test across multiple storage backends. Each storage has its own `docker-compose.yml` and optionally its own `e2e.yaml`. Expected files are usually shared (referenced via relative paths like `../../expected/`).

### Environment variables
All tests share `script/env` for version pinning. Common variables:
- `${oap_host}`, `${oap_12800}`, `${oap_9090}`, etc. — OAP server connection
- `${provider_host}`, `${consumer_host}` — Test service hosts
- `${provider_9090}`, `${consumer_9092}` — Test service ports

### Running e2e tests locally
```bash
# From skywalking root, with infra-e2e installed:
e2e run -c test/e2e-v2/cases/<feature>/<storage>/e2e.yaml
```

# Admin API

`admin-server` is SkyWalking OAP's shared HTTP host for **admin / on-demand
write APIs** — the operations operators perform from a jumphost or CI rather
than telemetry-time queries. This document is the entry point for every
endpoint hosted there.

## ⚠️ Security notice

> The admin port has **no built-in authentication** in this release.
> Anything that can reach the port can call every endpoint listed below.
> Several endpoints (DSL debug capture, runtime-rule writes) expose or
> modify operationally-sensitive state.
>
> **Operators MUST:**
>
> 1. **Gateway-protect the admin port** with an IP allow-list and an
>    authenticating reverse proxy (sidecar, gateway, mTLS terminator).
>    SkyWalking ships `admin-server` **enabled by default** so the status
>    feature module is reachable out of the box; the host binds to
>    `0.0.0.0:17128` by default. Set `SW_ADMIN_SERVER=` (empty) to disable
>    entirely.
> 2. **Bind to a private interface.** Set `SW_ADMIN_SERVER_HOST` to a private
>    address (or `127.0.0.1`) — never the public-facing interface.
> 3. **Never expose the admin port to the public internet.** No exception.
> 4. **Audit access.** The reverse proxy in front of admin-server is the
>    canonical access log for these endpoints; OAP itself does not record
>    per-request authorization decisions.
>
> The DSL debug API (below) is read-side and captures telemetry payloads
> including raw log bodies, parsed maps, extracted output fields, and
> typed builder state. Treat the admin port as authenticated infrastructure
> and the captured payloads as sensitive data.

## Endpoints by feature

`admin-server` itself only provides the HTTP host. Each feature module
registers its own routes on it. Today there are four:

### [Runtime Rule Hot-Update API](runtime-rule.md)

Operators add, override, inactivate, and delete MAL / LAL rule files at
runtime without restarting OAP. Routes live under `/runtime/rule/*`.
Requires `SW_RECEIVER_RUNTIME_RULE=default` (`admin-server` is on by default).

Common operations:

- `POST /runtime/rule/addOrUpdate?catalog=&name=` — push a new or updated rule.
- `POST /runtime/rule/inactivate?catalog=&name=` — turn a rule off.
- `POST /runtime/rule/delete?catalog=&name=` — remove a rule.
- `GET /runtime/rule/list` — current rules + per-node state.
- `GET /runtime/rule/dump` — tar.gz snapshot of all rules.

Full reference: [Runtime Rule Hot-Update API](runtime-rule.md).

### [DSL Debug API](dsl-debugging.md)

Operators run sampling debug sessions that capture how MAL / LAL / OAL
rules transform live ingest, with per-stage / per-block payloads
available for inspection. Routes live under `/dsl-debugging/*` and
`/runtime/oal/*` (read-only OAL rule listing for the debugger's rule
picker). Requires `SW_DSL_DEBUGGING=default` (`admin-server` is on by default).

Common operations:

- `POST /dsl-debugging/session` — start a debug capture session.
- `GET /dsl-debugging/session/{id}` — poll captured records.
- `POST /dsl-debugging/session/{id}/stop` — stop a session.
- `GET /dsl-debugging/status` — current posture (active sessions, injection state).
- `GET /runtime/oal/files` — list loaded `.oal` files (debug picker source).
- `GET /runtime/oal/rules/{ruleName}` — single OAL rule detail.

Design reference: [SWIP-13](../../../swip/SWIP-13.md).
Operator reference: [DSL Debug API](dsl-debugging.md).

### [Inspect API](inspect.md)

Operators browse the live metric catalog and the entities currently emitting
values for a given metric, getting MQE-ready output that pastes directly into
the public GraphQL `execExpression` mutation. Routes live under `/inspect/*`.
Enabled by default (both `SW_INSPECT=default` and `SW_ADMIN_SERVER=default` are on
by default); set `SW_INSPECT=` (empty) to disable.

Common operations:

- `GET /inspect/metrics` — metric catalog with type / scope / supported downsamplings.
- `GET /inspect/entities?metric=&start=&end=&step=` — capped (≤300) list of
  entities holding values, decoded into MQE-ready form.
- `POST /inspect/values` — read the value series of a metric this OAP does not
  define locally (foreign metric), by supplying its `{valueColumn, valueType}`;
  returns the native MQE result.

Operator reference: [Inspect API](inspect.md).

### [Status API](status.md)

Cluster, alarm, TTL, and per-query debug-trace endpoints. Hosted by the
`status` feature module on the admin-server REST port (default `17128`),
alongside `/ui-management/*`, `/inspect/*`, `/dsl-debugging/*`, and
`/runtime/rule/*`. Both `status` and `admin-server` are enabled by
default — no opt-in required.

Common operations:

- `GET /status/cluster/nodes` — OAP cluster peer list.
- `GET /status/alarm/rules`, `/status/alarm/{ruleId}`, `/status/alarm/{ruleId}/{entityName}` — alarm runtime state.
- `GET /status/config/ttl` — effective TTL configuration.
- `GET /debugging/config/dump` — effective configuration dump (also serves as the REST-URL discovery primitive for inspect clients).
- `GET /debugging/query/...` — run a named query path with debug tracing enabled.

Operator reference: [Status API](status.md).

### [UI Management API](ui-management.md)

REST surface for dashboard templates (consumed by
[Horizon UI](https://github.com/apache/skywalking-horizon-ui)). Routes
live under `/ui-management/templates/*`. Enabled by default
(`SW_UI_MANAGEMENT=default`, `admin-server` is on by default).

Common operations:

- `GET /ui-management/templates` — list all templates.
- `GET /ui-management/templates/{id}` — fetch a single template.
- `POST /ui-management/templates` — add a new template (server-allocated UUID).
- `PUT /ui-management/templates` — update an existing template.
- `POST /ui-management/templates/{id}/disable` — soft-disable a template.

The sidebar menu is **not** served from OAP — Horizon UI owns it
client-side and uses `listServices(layer: ...)` on the metadata query
surface for dynamic "layer has services" gating.

Operator reference: [UI Management API](ui-management.md).

### Admin UI

The official web UI is [Horizon UI](https://github.com/apache/skywalking-horizon-ui),
a SkyWalking sub-project. Horizon UI consumes both the public query
surface (GraphQL / MQE on `core.restPort`, default `12800`) and every
admin host endpoint listed above (`/ui-management/*`, `/status/*`,
`/inspect/*`, `/dsl-debugging/*`, `/runtime/rule/*` on the admin port,
default `17128`). Operators who prefer not to deploy a web UI can drive
every admin endpoint with `curl`, `swctl`, or a front-end of their
choice.

## Enabling

`admin-server` itself is enabled by default; the feature modules that
mount onto it are individually opt-in. Enable the features the deployment
needs:

```bash
# SW_ADMIN_SERVER=default is on by default — see security notice above.
# SW_STATUS=default is on by default — see [Status API](status.md).
# SW_INSPECT=default is on by default — see [Inspect API](inspect.md).
export SW_RECEIVER_RUNTIME_RULE=default   # to use /runtime/rule/*
export SW_DSL_DEBUGGING=default           # to use /dsl-debugging/* and /runtime/oal/*
```

If a feature module is enabled without `admin-server`, OAP fails fast at
startup with a `ModuleNotFoundException: admin-server` — same mechanism
that gates every other module dependency in OAP.

## Configuration

`admin-server` runs on **two ports**:

| Port | Default | Direction | Purpose |
|------|---------|-----------|---------|
| `port` | `17128` | inbound from operators | HTTP REST surface (operator API) |
| `gRPCPort` | `17129` | peer-to-peer between OAP nodes | admin-internal gRPC bus (cluster RPCs for runtime-rule Suspend/Resume/Forward, dsl-debugging install/collect/stop) |

Privileged admin RPCs ride the **dedicated `17129` gRPC server** rather than the public agent / cluster gRPC port (`core.gRPCPort`, default `11800`). This isolation is the core security property of `admin-server`: an attacker on the agent network can reach `11800` for legitimate telemetry submission, but cannot reach `17129` if the operator binds it to a private peer-to-peer interface. Admin RPCs and agent telemetry never share a blast radius.

Operators MUST:
1. Bind `port` (HTTP) only to a private operator interface; gateway-protect with an IP allow-list and authenticating reverse proxy.
2. Bind `gRPCPort` (admin-internal) only to a private peer-to-peer network; NEVER expose to operators or to the agent network.
3. Use the same `gRPCPort` value on every OAP node — peers are dialed at the LOCAL config's port, uniform across the cluster by convention.

```yaml
admin-server:
  selector: ${SW_ADMIN_SERVER:default}
  default:
    # HTTP REST surface (operator API).
    host: ${SW_ADMIN_SERVER_HOST:0.0.0.0}
    port: ${SW_ADMIN_SERVER_PORT:17128}
    contextPath: ${SW_ADMIN_SERVER_CONTEXT_PATH:/}
    idleTimeOut: ${SW_ADMIN_SERVER_IDLE_TIMEOUT:30000}
    acceptQueueSize: ${SW_ADMIN_SERVER_QUEUE_SIZE:0}
    httpMaxRequestHeaderSize: ${SW_ADMIN_SERVER_HTTP_MAX_REQUEST_HEADER_SIZE:8192}
    # Admin-internal gRPC bus (peer-to-peer cluster RPCs only).
    gRPCHost: ${SW_ADMIN_SERVER_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_ADMIN_SERVER_GRPC_PORT:17129}
    gRPCMaxConcurrentCallsPerConnection: ${SW_ADMIN_SERVER_GRPC_MAX_CONCURRENT_CALL:0}
    gRPCMaxMessageSize: ${SW_ADMIN_SERVER_GRPC_MAX_MSG_SIZE:52428800}
    gRPCThreadPoolSize: ${SW_ADMIN_SERVER_GRPC_THREAD_POOL_SIZE:0}
    gRPCSslEnabled: ${SW_ADMIN_SERVER_GRPC_SSL_ENABLED:false}
    gRPCSslKeyPath: ${SW_ADMIN_SERVER_GRPC_SSL_KEY_PATH:""}
    gRPCSslCertChainPath: ${SW_ADMIN_SERVER_GRPC_SSL_CERT_CHAIN_PATH:""}
    gRPCSslTrustedCAsPath: ${SW_ADMIN_SERVER_GRPC_SSL_TRUSTED_CAS_PATH:""}
    # Default per-call timeout (ms) for admin-internal RPCs between OAP nodes
    # (dsl-debugging install / collect / stop). Workflow-specific call sites
    # — runtime-rule Suspend / Forward — keep their own tuned values.
    internalCommunicationTimeout: ${SW_ADMIN_SERVER_INTERNAL_COMM_TIMEOUT:5000}
```

See each feature's API reference for its own configuration block.

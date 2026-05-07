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
>    SkyWalking ships `admin-server` *disabled by default* (`SW_ADMIN_SERVER`
>    selector empty); enabling it is an explicit operator decision.
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
registers its own routes on it. Today there are two:

### [Runtime Rule Hot-Update API](runtime-rule.md)

Operators add, override, inactivate, and delete MAL / LAL rule files at
runtime without restarting OAP. Routes live under `/runtime/rule/*`.
Requires `SW_RECEIVER_RUNTIME_RULE=default` (which in turn requires
`SW_ADMIN_SERVER=default`).

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
picker). Requires `SW_DSL_DEBUGGING=default` (which in turn requires
`SW_ADMIN_SERVER=default`).

Common operations:

- `POST /dsl-debugging/session` — start a debug capture session.
- `GET /dsl-debugging/session/{id}` — poll captured records.
- `POST /dsl-debugging/session/{id}/stop` — stop a session.
- `GET /dsl-debugging/status` — current posture (active sessions, injection state).
- `GET /runtime/oal/files` — list loaded `.oal` files (debug picker source).
- `GET /runtime/oal/rules/{ruleName}` — single OAL rule detail.

Design reference: [SWIP-13](../../../swip/SWIP-13.md).
Operator reference: [DSL Debug API](dsl-debugging.md).

## Enabling

`admin-server` and its feature modules ship disabled by default. Enable
the features the deployment needs:

```bash
export SW_ADMIN_SERVER=default            # the shared HTTP host
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
  selector: ${SW_ADMIN_SERVER:-}
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

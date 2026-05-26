# Status API

The Status API is a set of read-only HTTP endpoints for inspecting cluster
membership, alarm runtime state, effective configuration / TTL settings,
and per-query debug traces. Hosted by the `status` feature module on the
admin-server REST host (default `17128`), alongside `/ui-management/*`,
`/inspect/*`, `/dsl-debugging/*`, and `/runtime/rule/*`. One handler —
`/status/config/ttl` — is also bound on the public REST host
(default `12800`) so ecosystem tools that discover TTL via REST before
issuing `/graphql` can fetch it without being aware of the admin port.

## Hosting

`status` registers all handlers on the admin-server REST host (default port
`17128`). Both `status` and `admin-server` are enabled by default, so the
surface is reachable out of the box. The admin port is gateway-protected per
the [admin-server security notice](readme.md#security-notice). To disable
status explicitly, unset `SW_STATUS`:

```bash
export SW_STATUS=                       # disable
export SW_STATUS=default                # default (enabled)
```

## Configuration

```yaml
status:
  selector: ${SW_STATUS:default}
  default:
    keywords4MaskingSecretsOfConfig: ${SW_DEBUGGING_QUERY_KEYWORDS_FOR_MASKING_SECRETS:user,password,trustStorePass,keyStorePass,token,accessKey,secretKey,authentication}
```

`keywords4MaskingSecretsOfConfig` is consumed by `/debugging/config/dump`
to redact configuration values whose key contains any listed substring.

## Endpoints

### `/status/cluster/nodes`

Returns the OAP cluster peer list as the cluster module sees it. Useful
for confirming that every node has joined and is reporting back.

```bash
curl http://oap:17128/status/cluster/nodes
```

### `/status/alarm/rules`

Lists registered alarm rules.

### `/status/alarm/{ruleId}`

Per-rule runtime status — current window state, last evaluation, the
entities the rule has triggered against.

### `/status/alarm/{ruleId}/{entityName}`

Drill-down: status of one specific entity under a given rule.

### `/status/config/ttl`

Returns the effective TTL configuration the OAP loaded at boot.
**Reachable on both ports** — `:17128` (admin) and `:12800` (public) —
so ecosystem tools can call it without admin-port knowledge. Every
other `/status/*` handler is admin-only.

### `/debugging/config/dump`

Dumps the effective configuration that was applied at boot. Values whose
key contains any substring listed in `keywords4MaskingSecretsOfConfig` are
redacted. Output is YAML-shaped key=value lines.

The Inspect API uses this endpoint as its REST-URL discovery primitive —
clients parse the dump for `core.restHost` / `core.restPort` (or the
sharing-server overrides) once at session start to learn where the public
GraphQL / MQE surface lives.

### `/debugging/query/...`

Runs a named query path with debug tracing enabled and returns the
captured DAO / storage spans alongside the result. Useful for diagnosing
why a query is slow or returning unexpected data.

| URI | Purpose |
|---|---|
| `/debugging/query/mqe` | Run an MQE expression with tracing. |
| `/debugging/query/trace/queryBasicTraces` | Trace search brief. |
| `/debugging/query/trace/queryTrace` | Trace detail. |
| `/debugging/query/zipkin/api/v2/traces` | Zipkin compat brief. |
| `/debugging/query/zipkin/api/v2/trace` | Zipkin compat detail. |
| `/debugging/query/topology/getGlobalTopology` | Global topology debug. |
| `/debugging/query/topology/getServicesTopology` | Per-service topology debug. |
| `/debugging/query/topology/getServiceInstanceTopology` | Per-instance topology debug. |
| `/debugging/query/topology/getEndpointDependencies` | Endpoint dependencies debug. |
| `/debugging/query/topology/getProcessTopology` | Process topology debug. |
| `/debugging/query/log/queryLogs` | Log query debug. |

The query parameters mirror the corresponding GraphQL inputs (consult the
schema definitions under
`oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol`).

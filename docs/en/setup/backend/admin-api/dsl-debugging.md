# DSL Debug API

> Status: **shipped**. Sample-based runtime debugger for the three DSLs that
> drive SkyWalking analysis: **MAL** (meter analysis language), **LAL** (log
> analysis language), and **OAL** (observability analysis language). Design:
> [SWIP-13](../../../swip/SWIP-13.md).

## Per-DSL operator references

Each DSL has its own probe surface, payload shape, and rule-key conventions
— pick the page that matches the rule you're debugging:

- **[MAL](dsl-debugging-mal.md)** — meter rules under `otel-rules`, `log-mal-rules`, `telegraf-rules`. Each captured record is one `SampleFamily` walking through the rule end-to-end (filter → chain ops → meterEmit). Sample payloads carry the complete `SampleFamily` (every sample's name + labels + value + timestamp).
- **[OAL](dsl-debugging-oal.md)** — per-metric dispatcher capture under `catalog=oal`. Each record is one `ISource` walking through (source entry → filter clauses → aggregation function → emit). Source samples carry the rich `ServiceRelation`-style payload (sourceServiceName, destServiceName, layers, latency, status, detectPoint, ...).
- **[LAL](dsl-debugging-lal.md)** — log analysis under `catalog=lal`. Each record is one log walking through (text → parser → extractor statements → sink). `granularity=statement` emits one sample per extractor statement; `granularity=block` (default) collapses extractor into one sample.

## Kept-only capture for MAL (filter is the relevance gate)

**MAL** is the only DSL where rejected executions are dropped from
`records[]`. The reason is tag-cardinality noise: MAL filters
discriminate on tag values (`service_name == 'foo'`,
`kind == 'http_server_request_duration'`) and in a multi-tenant or
multi-component flow, a single rule may reject 99% of the traffic
routed to it. If every rejection landed as a record, the default
`recordCap` (32) would fill with noise within seconds and the capture
would never reach a row demonstrating the rule's actual processing.

So for MAL the contract is: **every record represents one
SampleFamily that passed the rule's filter clause and walked through
to `meterEmit`**. The first sample is the input that the filter
accepted; subsequent samples follow the chain ops; the last is the
emit.

**OAL** captures both kept and rejected executions. OAL filters are
deterministic discriminators (CLIENT-vs-SERVER, layer matchers,
status / latency predicates), not high-cardinality tag noise — seeing
the rejected-source samples (`continueOn=false`) is the filter doing
its job in plain view, useful for verifying partition logic.

**LAL** has no analogue (`abort()` is a per-statement short-circuit,
not a filter); aborted logs publish their accumulated samples with
`continueOn=false` on the abort point so operators can see where the
rule gave up.

For MAL operators debugging "why isn't my data being aggregated?":
the runtime-rule list endpoint surfaces the rule's filter source;
combine with knowledge of the input tags to identify the mismatch.
The debug API's job is to show what the rule did with traffic it
accepted.

## Common ground

What the three DSLs share:

- **One REST surface**: routes mount under `/dsl-debugging/*` on the
  `admin-server` HTTP host (default port `17128`). The shape of session
  lifecycle (install, poll, stop) is identical across DSLs — only the
  rule-key tuple and payload schema differ.
- **One enable contract**: requires `SW_ADMIN_SERVER=default` and
  `SW_DSL_DEBUGGING=default`. Probe insertion is gated by the boot-time
  switch `SW_DSL_DEBUGGING_INJECTION_ENABLED` (default `true` — once the
  module is enabled, probes fire and sessions record samples). Set this
  to `false` only if the REST surface is wanted but probe overhead is
  not; flipping requires an OAP restart.
- **One cluster contract**: install / collect / stop broadcast to every
  reachable peer over the **admin-internal gRPC bus** (default port
  `17129`, owned by `admin-server`) — NOT the public agent / cluster
  gRPC port (`core.gRPCPort`, default `11800`). Privileged admin RPCs
  stay off the agent network; operators bind `17129` to a private
  peer-to-peer interface only. Each peer's slice is self-contained — no
  L2 join.
- **One retention contract**: per-session `recordCap` (default `1000`,
  hard cap `10000`) and `retentionMillis` (default 5 minutes, hard cap
  1 hour). Requests outside these bounds return `400 invalid_limits`.
  A missed `stop` self-cleans on retention timeout.
- **LB-safe routing**: any node can serve any verb. POST mints the
  `sessionId` on the receiving node, broadcasts install to all peers,
  and reports per-peer install state in `peers[]`. GET/stop on a
  different node aggregate the cluster slices for the same id. So an
  L7 LB in front of the admin port (default port `17128`) can route
  any request to any OAP, and the cluster fan-out hides which node
  actually owns the rule.
- **One timestamp per slice**: the GET response envelope carries a
  single `capturedAt` (millis) stamped at GET time. Per-record stamps
  were redundant — probes fire at clock-tick speed and intra-slice
  ordering is preserved by array order.
- **`sourceText` is the verbatim ANTLR slice**: pulled via
  `ctx.getStart().getInputStream().getText(Interval.of(start, stop))` at
  parse time, so whitespace and identifier spelling are byte-identical
  to the original `.yaml` / `.oal`. This applies to MAL chain segments,
  OAL filter clauses, OAL aggregation calls, and LAL extractor
  statements. Operators can grep the captured `sourceText` against the
  source file directly.

Each DSL's page has its own walkthrough — start there for a copy-paste
example targeting that DSL's rules.

## Session lifecycle

| Verb | Path                                                                                        | Purpose                                  |
|------|---------------------------------------------------------------------------------------------|------------------------------------------|
| POST | `/dsl-debugging/session?catalog=&name=&ruleName=&clientId=&granularity=`                    | Start a debug capture session.            |
| GET  | `/dsl-debugging/session/{id}`                                                               | Snapshot the captured records.            |
| POST | `/dsl-debugging/session/{id}/stop`                                                          | Stop a session (idempotent).              |
| GET  | `/dsl-debugging/sessions`                                                                   | List active sessions on this node.        |
| GET  | `/dsl-debugging/status`                                                                     | Module posture (phase, injectionEnabled). |

`granularity` is LAL-only; MAL/OAL recorders ignore it. Body fields
(`recordCap`, `retentionMillis`, `granularity`) are optional; defaults from
the table above apply.

## OAL rule picker (read-only)

The DSL debug API also exposes a read-only listing of the OAL rules
loaded by an OAP node, used by the debugger UI's rule picker:

| Verb | Path                            | Purpose                                              |
|------|---------------------------------|------------------------------------------------------|
| GET  | `/runtime/oal/files`            | List the OAL files this OAP loaded at boot, with their sources and registered rules. |
| GET  | `/runtime/oal/files/{name}`     | Raw OAL content (UTF-8 text, classpath-bound).       |
| GET  | `/runtime/oal/rules/{source}`   | Rules registered against a single source (debug-key resolver). |

These do not require `injectionEnabled=true` — they are pure metadata.

## Failure modes

Common across DSLs. Per-DSL pages list any DSL-specific failures.

| Response                     | Meaning                                                                            |
|------------------------------|------------------------------------------------------------------------------------|
| `400 invalid_catalog`        | Wire `catalog` doesn't match any known DSL.                                         |
| `400 missing_param`          | `name`, `ruleName`, or `clientId` query param missing. `clientId` is required so the receiving node can clean up the prior session bound to it before installing a new one. |
| `400 invalid_limits`         | `recordCap` / `retentionMillis` outside the per-session bounds (see above).         |
| `429 too_many_sessions`      | Active-session ceiling on the receiving node (200) is full. Stop a session or wait for retention. |
| `404 rule_not_found`         | No live DSL artifact for `(catalog, name, ruleName)` on **any reachable** OAP node — every node and peer reported `NOT_LOCAL`. The 404 body carries `peers[]` so operators can see which OAP rejected what. |
| `404 session_not_found`      | Session id unknown to **every reachable** OAP node — never created, retention timed out, or already stopped. |
| `503 injection_disabled`     | `injectionEnabled=false`; capture is permanently off until OAP restarts with the flag on. |
| `500 registry_misconfigured` | A recorder factory wiring bug — file an issue.                                      |

## Configuration reference

See [Configuration vocabulary](../configuration-vocabulary.md) for the full
table. The DSL-debug-relevant keys:

| Key                                  | Default | Purpose                                                       |
|--------------------------------------|---------|---------------------------------------------------------------|
| `SW_ADMIN_SERVER`                    | (empty) | Enables the shared admin HTTP host. Must be set.              |
| `SW_DSL_DEBUGGING`                   | (empty) | Enables the DSL debug API.                                    |
| `SW_DSL_DEBUGGING_INJECTION_ENABLED` | `true`  | Boot-time codegen switch. Set `false` to disable probes.      |

> SECURITY: capture payloads include sensitive operational state (raw log
> bodies, parsed maps, MAL builder state, OAL source events). Treat the
> admin port as authenticated infrastructure — see
> [Admin API readme — Security Notice](readme.md#security-notice).

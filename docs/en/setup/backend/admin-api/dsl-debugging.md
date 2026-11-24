# DSL Debug API

> Status: **shipped**. Sample-based runtime debugger for the four DSLs that
> drive SkyWalking analysis: **MAL** (meter analysis language), **LAL** (log
> analysis language), **OAL** (observability analysis language), and **TAL**
> (trace analysis language). Design: [SWIP-13](../../../swip/SWIP-13.md).

## Per-DSL operator references

Each DSL has its own probe surface, payload shape, and rule-key conventions
— pick the page that matches the rule you're debugging:

- **[MAL](dsl-debugging-mal.md)** — meter rules under `otel-rules`, `log-mal-rules`, `telegraf-rules`, `meter-analyzer-config`. Each captured record is one `SampleFamily` walking through the rule end-to-end (filter → chain ops → meterEmit). Sample payloads carry the complete `SampleFamily` (every sample's name + labels + value + timestamp).
- **[OAL](dsl-debugging-oal.md)** — per-metric dispatcher capture under `catalog=oal`. Each record is one `ISource` walking through (source entry → filter clauses → aggregation function → emit). Source samples carry the rich `ServiceRelation`-style payload (sourceServiceName, destServiceName, layers, latency, status, detectPoint, ...).
- **[LAL](dsl-debugging-lal.md)** — log analysis under `catalog=lal`. Each record is one log walking through (text → parser → extractor statements → sink). `granularity=statement` emits one sample per extractor statement; `granularity=block` (default) collapses extractor into one sample.
- **[TAL](dsl-debugging-tal.md)** — trace analysis under `catalog=tal`. A record follows the object the analyzer passes through the rule: one OTLP span, or one native segment. Every record is a span list; each span carries the rule's run on it (the statements it executed, its outputs, its end state), not samples. `granularity` works as for LAL.

## Kept-only capture for MAL (filter is the relevance gate)

**MAL** is the only DSL where rejected executions are dropped from
`records[]`. The reason is tag-cardinality noise: MAL filters
discriminate on tag values (`service_name == 'foo'`,
`kind == 'http_server_request_duration'`) and in a multi-tenant or
multi-component flow, a single rule may reject 99% of the traffic
routed to it. If every rejection landed as a record, the default
`recordCap` (100) would fill with noise within seconds and the capture
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
rule gave up. **TAL** does the same: an aborted run is published with
`continueOn=false` and the abort line as its `reason`.

For MAL operators debugging "why isn't my data being aggregated?":
the runtime-rule list endpoint surfaces the rule's filter source;
combine with knowledge of the input tags to identify the mismatch.
The debug API's job is to show what the rule did with traffic it
accepted.

## Common ground

What the four DSLs share:

- **One REST surface**: routes mount under `/dsl-debugging/*` on the
  `admin-server` HTTP host (default port `17128`). The shape of session
  lifecycle (install, poll, stop) is identical across DSLs — only the
  rule-key tuple and payload schema differ.
- **One enable contract**: requires `SW_DSL_DEBUGGING=default`
  (`admin-server` itself is enabled by default). Probe insertion is gated by the boot-time
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
- **One retention contract**: per-session `recordCap` (default and
  hard cap `100`) and `retentionMillis` (default 5 minutes, hard cap
  1 hour). Requests outside these bounds return `400 invalid_limits`.
  A missed `stop` self-cleans on retention timeout.
- **LB-safe routing**: any node can serve any verb. POST mints the
  `sessionId` on the receiving node, broadcasts install to all peers,
  and reports per-peer install state in `peers[]`. GET/stop on a
  different node aggregate the cluster slices for the same id. So an
  L7 LB in front of the admin port (default port `17128`) can route
  any request to any OAP, and the cluster fan-out hides which node
  actually owns the rule.
- **Paged reads**: every record carries `captureTime`, when its node
  captured it, and a GET returns the records captured at or after the
  point the client passes, which the previous response returned as
  `nextCapturedSince`; see
  [Reading the records page by page](#reading-the-records-page-by-page).
  The point travels with the client, so any node serves the next page.
  The response envelope's `capturedAt` (millis) is when it was built.
- **`sourceText` is the verbatim ANTLR slice**: pulled via
  `ctx.getStart().getInputStream().getText(Interval.of(start, stop))` at
  parse time, so whitespace and identifier spelling are byte-identical
  to the original `.yaml` / `.oal`. This applies to MAL chain segments,
  OAL filter clauses, OAL aggregation calls, LAL extractor
  statements, and TAL statements (a TAL statement with a body shows its
  first line). Operators can grep the captured `sourceText` against the
  source file directly.

Each DSL's page has its own walkthrough — start there for a copy-paste
example targeting that DSL's rules.

## Session lifecycle

| Verb | Path                                                                                        | Purpose                                  |
|------|---------------------------------------------------------------------------------------------|------------------------------------------|
| POST | `/dsl-debugging/session?catalog=&name=&ruleName=&clientId=&granularity=`                    | Start a debug capture session.            |
| GET  | `/dsl-debugging/session/{id}?capturedSince=&pageSize=`                                      | Read a page of the captured records, see [Reading the records page by page](#reading-the-records-page-by-page). |
| POST | `/dsl-debugging/session/{id}/stop`                                                          | Stop a session (idempotent).              |
| GET  | `/dsl-debugging/sessions`                                                                   | List active sessions on this node.        |
| GET  | `/dsl-debugging/status`                                                                     | Module posture (injectionEnabled, active sessions, and `catalogs`, the DSLs a session can be installed on). |

`granularity` applies to LAL and TAL; MAL/OAL recorders ignore it. Body fields
(`recordCap`, `retentionMillis`, `granularity`) are optional; defaults from
the table above apply.

## Reading the records page by page

Every node holds its own records in memory until the session stops, its retention ends, or the same `clientId`
starts another session. Each record carries `captureTime`, when its node captured it: the node's wall clock as
`<millis>.<nanos>`, epoch milliseconds and the nanoseconds within that millisecond in six digits, such as
`1795505219786.871986`. Two times compare by their milliseconds, then their nanoseconds; with both parts at a fixed
width, plain string order is time order. On a JDK whose clock ticks in microseconds, as JDK 11's does, the last three
digits are `000`; the image's JDK keeps them.

A read returns the records captured at or after `capturedSince`, from the first one when it is not given, from every
node, grouped by node as before, and before the response's `nextCapturedSince`. Every record captured before
`nextCapturedSince`, on every node that answered, is in this response or an earlier one; the next read passes it back
as `capturedSince` and gets what was captured since, so a record captured at the point itself comes with the next
read. Everything the next page needs travels with the client, so the read may land on any node. An empty
`nextCapturedSince` means read from the first record again.

- `pageSize` bounds the records of one response, across the nodes: 1 to 1000, by default 1000. Records of one capture
  time stay on one page, so a page can hold a few more when two nodes, or one node's clock tick, captured several at
  once. Whatever it asks for, a response holds at most 100 MiB, which the nodes share evenly, so the page size is what
  a read meets. A node always sends its next record, so a page moves on even when that record alone is larger than
  its share.
- Another node streams its part to the node serving the read, one record a message, so only a single record has to
  fit a message between nodes: `admin-server.gRPCMaxMessageSize` (`SW_ADMIN_SERVER_GRPC_MAX_MSG_SIZE`, 50 MB by
  default). A larger record comes from another node as its `startedAtMs`, `captureTime`, `rule` and
  `oversizedBytes`, its size, without its content. A node of an earlier version, which has no such stream, shows as
  `unreachable` to this one and the other way round, until every node runs the same version.
- A node whose page was cut short, or whose clock runs behind the others while it still captures, holds
  `nextCapturedSince` back, and the others' later records come with the next read; a node's clock running behind by
  some time delays the others' records by that much. A node that reached its `recordCap` captures no more, so its
  clock holds back nothing once its records are read.
- `complete` is `true` when the capture is read through: every node holding the session answered, reached its
  `recordCap`, and this response holds the rest of its records. `nextCapturedSince` is then the latest capture time,
  `9223372036854775807.999999`, and a read from it returns no records. A node that still captures, or one that did
  not answer, keeps `complete` `false`.
- Each node's slice carries `instanceId`, which tells that OAP process apart from every other, a restarted one
  included. `nodeId` does not: every node that binds `0.0.0.0` reports the same one, and the node serving a read names
  itself without `peer`, so a client folding pages read through a load balancer keys the slices by `instanceId`. A
  node that did not answer has no `instanceId` in that response, only its `peer` address.
- A node that does not answer has no records in that response. When another node holding the session answered, the
  serving node's clock stands in for its point, and the records it captured before the response's
  `nextCapturedSince` are not read again; when none did, the point stays where the read gave it. Nor are the records a node captures after its clock steps back, as NTP can
  step it to correct a large offset, while their times are before a point already returned.
- An OAP before this API ignores both parameters and returns every record each time, without
  `nextCapturedSince`; a client that gets no `nextCapturedSince` reads each response as the whole capture.
- `capturedAt` at the top of a response is when the response was built, in milliseconds.

## OAL rule picker (read-only)

The DSL debug API also exposes a read-only listing of the OAL rules
loaded by an OAP node, used by the debugger UI's rule picker:

| Verb | Path                            | Purpose                                              |
|------|---------------------------------|------------------------------------------------------|
| GET  | `/runtime/oal/files`            | List the OAL files this OAP loaded at boot, with their sources and registered rules. |
| GET  | `/runtime/oal/files/{name}`     | Raw OAL content (UTF-8 text, classpath-bound).       |
| GET  | `/runtime/oal/rules/{source}`   | Rules registered against a single source (debug-key resolver). |

These do not require `injectionEnabled=true` — they are pure metadata.

## TAL rule listing (read-only)

The TAL rules a node loaded at boot, for the rule picker and the rule view. See
[Picking the rule key](dsl-debugging-tal.md#picking-the-rule-key).

| Verb | Path                            | Purpose                                              |
|------|---------------------------------|------------------------------------------------------|
| GET  | `/runtime/tal/files`            | Every loaded TAL file with its rules.                |
| GET  | `/runtime/tal/files/{name}`     | A loaded file's content (text).                      |

These do not require `injectionEnabled=true` either.

## Failure modes

Common across DSLs. Per-DSL pages list any DSL-specific failures.

| Response                     | Meaning                                                                            |
|------------------------------|------------------------------------------------------------------------------------|
| `400 invalid_catalog`        | Wire `catalog` doesn't match any known DSL.                                         |
| `400 missing_param`          | `name`, `ruleName`, or `clientId` query param missing. `clientId` is required so the receiving node can clean up the prior session bound to it before installing a new one. |
| `400 invalid_limits`         | `recordCap` / `retentionMillis` outside the per-session bounds (see above).         |
| `400 invalid_page`           | `pageSize` outside 1 to 1000, or a `capturedSince` that is not `<millis>.<six-digit nanos>`. |
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
| `SW_ADMIN_SERVER`                    | `default` | Shared admin HTTP host. On by default; set `-` to disable, together with every feature module on it. |
| `SW_DSL_DEBUGGING`                   | `default` | Enables the DSL debug API. On by default; set `-` to disable. |
| `SW_DSL_DEBUGGING_INJECTION_ENABLED` | `true`  | Boot-time codegen switch. Set `false` to disable probes.      |

> SECURITY: capture payloads include sensitive operational state (raw log
> bodies, parsed maps, MAL builder state, OAL source events, TAL spans with
> their attributes and tags). Treat the
> admin port as authenticated infrastructure — see
> [Admin API readme — Security Notice](readme.md#security-notice).

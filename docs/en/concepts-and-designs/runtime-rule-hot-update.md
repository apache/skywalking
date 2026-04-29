# Runtime Rule Hot-Update — Architecture

Operators change MAL and LAL rule files at runtime without restarting OAP. Changes
persist in management storage, survive reboots, and propagate across every node in
an OAP cluster within a bounded window. This page explains the contract: what the
feature guarantees, how the cluster stays consistent, and what to expect when
something goes wrong. The HTTP surface is documented separately in
[Runtime Rule Hot-Update API](../setup/backend/backend-runtime-rule-api.md).

## Vocabulary

- **Runtime rule entry** — the unit of operator state: one entry per `(catalog, name)`,
  carrying the full rule-file content plus a status of `ACTIVE` or `INACTIVE`. Entries
  live in management storage (the same persistence layer used for UI templates, UI
  menus, and other cluster-wide operator state).
- **Catalog** — the rule group named in the API, currently `otel-rules`, `log-mal-rules`,
  `telegraf-rules`, or `lal`. It mirrors the on-disk directory layout so a rule's
  `(catalog, name)` identity is portable between disk and the runtime-rule entry store.
- **Main** — the single OAP node designated to run the on-demand workflow. Every node
  can compute it locally from the sorted cluster peer list; no election.
- **Peer** — every node other than the main.
- **Periodic scan** — every OAP node re-reads management storage every 30 s (default,
  configurable) and brings its in-memory state into line with what storage holds. This
  is the convergence loop the consistency contract is built on.

## Scope: MAL and LAL, not OAL

Runtime hot-update covers only the **MAL** (`otel-rules`, `log-mal-rules`,
`telegraf-rules`) and **LAL** (`lal`) catalogs. OAL rules are deliberately out of
scope. Three reasons, in order of weight:

1. **OAL targets SkyWalking-native traffic sources; MAL and LAL target third-party
   data.** OAL rules derive metrics from the fixed set of sources the platform
   already knows how to collect — distributed traces, service / instance / endpoint
   traffic, Istio ALS records, native-agent telemetry. The source catalog doesn't
   change between deployments; OAL expresses the metrics SkyWalking itself exposes
   over that catalog. MAL and LAL are where third-party data lands: Prometheus
   scrapes, OpenTelemetry meters, Telegraf / Zabbix / SNMP pollers, log-to-metric
   extraction, custom receivers. New integrations, label cleanups, filter
   adjustments — the edits operators make most often — all live in MAL or LAL
   rule files.
2. **Operators iterate on MAL and LAL far more often than on OAL.** A new
   Prometheus target comes online, a log format changes upstream, a filter needs
   tightening to exclude a noisy source. These are production-frequency changes
   that a hot-update path removes the restart tax from. OAL edits, when they
   happen, are usually one-time decisions about which built-in metrics an
   installation exposes — decisions that fit the deployment cycle.
3. **OAL is a deeper integration; MAL and LAL are contained extension points.**
   OAL lives inside the analytical pipeline at the heart of SkyWalking. MAL and
   LAL already sit at a known extension boundary, so adding "change this
   configuration without restart" is a local concern. Touching the core to give
   OAL the same capability is a much larger effort and was deferred.

Operators who need to change OAL behavior still restart OAP, the way they did before
this feature. Everything below this section is scoped to MAL and LAL.

## The consistency contract

This is the headline. Everything else in this document derives from it.

> **Persist is commit. Every node converges to the persisted state on its next
> periodic scan. Healthy structural commits land cluster-wide within 30 s; aborted
> commits self-heal within 60 s. No quorum, no leader election, no two-phase
> protocol.**

Concretely:

- The runtime-rule entry in management storage is the **single source of truth**.
  Once `POST /addOrUpdate` returns 200, the entry is durable and every node in
  the cluster will eventually run that exact content.
- **Last write wins.** If two operators push the same rule to different nodes,
  whichever write hits storage second wins; the cluster converges on the next scan.
- **Local in-memory state is provisional.** A node can lag the entry briefly (during
  a periodic scan, during a structural apply, immediately after rejoining the
  cluster), but never indefinitely. Convergence bounds:

| Event                                                         | Convergence bound                                                            |
|---------------------------------------------------------------|------------------------------------------------------------------------------|
| Healthy structural commit                                     | ≤ 30 s for every peer (one scan).                                            |
| Main aborts mid-structural (after pause broadcast, before persist) | ≤ 60 s (peer self-heal).                                                |
| Main crashes mid-structural                                   | ≤ 60 s.                                                                      |
| Pause broadcast dropped to one peer                           | ≤ 30 s (the peer notices on the next scan).                                  |
| Peer partitioned during apply, rejoins later                  | ≤ 30 s after rejoin.                                                         |
| Two operators applying the same file to different nodes       | Last write wins; cluster converges within 30 s of the second commit.         |
| Management storage unavailable                                | In-memory state held stable; resumes within ≤ 30 s of storage return.        |

Operators reading `/runtime/rule/list` see two timestamps that make convergence
observable: the persisted `updateTime` (storage) and the per-node `localState`
(this OAP's in-memory view). When they agree the node is converged; when they
disagree a periodic scan is in flight or the node is mid-apply.

## Three workflows

The feature is three cooperating workflows:

1. **Boot** — OAP starts; static rule files on disk are loaded, with persisted
   runtime-rule entries substituted in or skipped at load time. Backend schema is
   read-only at boot — never reshaped, never dropped.
2. **On-demand** — an operator calls `POST /addOrUpdate`, `/inactivate`, or
   `/delete`. This is the **only** workflow that may change backend schema, because
   the operator explicitly asked for it.
3. **Periodic scan** — every OAP node re-reads every runtime-rule entry every 30 s
   and converges its local state to match. This is what closes the convergence
   bounds above; nothing else does.

```
                              ┌─────────────────────────┐
                              │   management storage    │ ← runtime-rule entries
                              │  (one entry per file)   │    (ACTIVE / INACTIVE)
                              └─────────────────────────┘
                                 ▲                ▲
                                 │ write          │ read
                                 │                │
  ┌─── boot ───┐         ┌── on-demand ──┐    ┌── periodic scan ─┐
  │ static     │         │ admin HTTP    │    │ every 30 s on    │
  │ files      │         │ add / update  │    │ every node       │
  │ on disk    │         │ inactivate    │    │                  │
  │    │       │         │ delete        │    │ diff entries vs  │
  │    ▼       │         │    │          │    │ in-memory state, │
  │ runtime    │         │    ▼          │    │ apply each delta │
  │ entry      │         │ pause peers   │    │                  │
  │ overrides  │         │ → storage     │    │ main: storage    │
  │ static or  │         │   check       │    │ peer: local      │
  │ skips it   │         │ → persist →   │    │ state only       │
  │            │         │   resume      │    │                  │
  └────────────┘         └───────────────┘    └──────────────────┘
```

## Boot workflow

At boot each analyzer module loads its static rule files from disk. The runtime-rule
plugin intercepts each file before compilation and decides per-`(catalog, name)`:

- No persisted entry → compile the disk file as-is.
- Persisted `ACTIVE` entry → compile the entry's content in place of the disk file.
- Persisted `INACTIVE` entry → skip the file; the operator has tombstoned the rule.

Compilation registers each rule under **create-if-absent** semantics: missing backend
resources are created; resources that already exist with a different shape are
**skipped with an ERROR log**, the affected metric is disabled until the operator
reconciles via `/addOrUpdate`. Boot is **never** allowed to silently reshape the
backend — that would mask edits made while OAP was offline.

After every analyzer has loaded, the runtime-rule plugin runs **one synchronous
scan** so any runtime-only entries (no static disk file) are applied before
receivers open ingress. From that point on, the periodic-scan workflow takes over.

### Why boot cannot reshape the backend

If boot were allowed to update backend storage, a shape mismatch could silently
rewrite the BanyanDB measure or the Elasticsearch mapping. Before this feature, the
backends behaved inconsistently:

- BanyanDB and JDBC silently accepted the mismatch — samples were written against
  the old schema and quietly truncated or rejected later.
- Elasticsearch hard-failed boot on a strict-mapping type conflict.

Create-if-absent unifies the contract: schema mismatches are always surfaced as an
explicit ERROR, boot always continues, the affected metric is disabled, and the
operator reconciles explicitly through the on-demand workflow. The same shape is
visible across every backend.

## On-demand workflow

Triggered by an HTTP call to one of the admin endpoints. A request arriving at any
node is forwarded to the **main** (the node selected from the cluster view); the main
runs the workflow and the receiving node relays the response to the operator.

Two paths, picked from the diff between the new content and the current entry:

- **Filter-only path** — body, filter, and tag tweaks that preserve every metric's
  storage identity. The main applies the change locally, persists the row, and
  returns. Peers pick up the new content on their next periodic scan and apply
  the same fast path. No cluster pause, no backend schema change, no alarm reset.
- **Structural path** — anything that moves metric identity (metric set added or
  removed, scope or downsampling function changed, LAL `(layer, ruleName)` set
  changed). The main runs:
  1. **Pause the cluster** — broadcast a pause to every peer over the cluster bus.
     Peers stop dispatching samples for the affected metrics and drain in-flight
     batches. Unreachable peers are logged and skipped; they self-recover via the
     periodic scan.
  2. **Update backend storage on this node**, including the schema-visibility fence on
     BanyanDB (see below).
  3. **Persist the entry** — this is the cluster-wide commit point.
  4. **Resume the cluster** — broadcast a resume so peers re-open dispatch. Peers
     that missed the resume self-heal within 60 s.
  5. **Reset alarm windows** for any metric whose identity changed, so accumulated
     state doesn't carry across the change.

If any step before persist fails, the entry is **not** advanced, the local node
rolls back to the previous rule state, peers self-heal back to the old content within
60 s, and the operator gets `HTTP 500` with `applyStatus` indicating the failure.

If persist itself fails, the same rollback happens — the durable state never moved,
so neither does the cluster.

If persist succeeds but the local finishing step fails (a rare path), the operator
gets `HTTP 500 commit_deferred`: storage holds the new content (peers will converge
on it), but this node hasn't fully applied it yet and will retry on its next scan.

### Lifecycle

A rule moves through three observable states:

```
  [absent] ──/addOrUpdate──► ACTIVE ──/inactivate──► INACTIVE ──/delete──► [absent]
                                ▲                         │
                                └─────/addOrUpdate────────┘   (reactivate)
```

The three endpoints split deactivation, destruction, and cleanup so an operator
never destroys data they might want back:

- **`/addOrUpdate`** is the only path that *enters* `ACTIVE`. It handles "new rule"
  and "reactivate" the same way — a post against an `INACTIVE` entry runs the full
  structural pipeline so backend schema and dispatch handlers are re-created from
  the posted bytes. Posting the same content against an `INACTIVE` row counts as a
  reactivation, not a no-op, because the *status* is what matters.
- **`/inactivate`** is the **soft-pause** path. The OAP-internal state for the rule
  is torn down (dispatch handlers removed, compiled rule dropped, alarm windows
  reset), but the **backend measure and its data are explicitly preserved**.
  Re-activation via `/addOrUpdate` reuses the existing measure; the cost is a
  recompile, not a backfill or a metric-identity change.
- **`/delete`** is the **destructive** endpoint — the **only** one that drops
  data. It refuses to operate on an `ACTIVE` row (returns `HTTP 409
  requires_inactivate_first`), so destruction always goes through the explicit
  two-step `/inactivate → /delete` workflow. On an `INACTIVE` row it drops the
  backend measure and removes the entry; on an absent row it is an idempotent
  `200 not_found`.

If a static version of the rule exists on disk, `/delete` of the runtime entry
causes the rule to revert to the static content on the next periodic scan. This is
the intended recovery path for "undo all operator state, go back to what ships in
the OAP distribution."

### Inactive rules still hold their names

`/inactivate` clears the runtime rule from memory but the entry is preserved with status
`INACTIVE`. The cross-file ownership guards (MAL metric names; LAL `(layer,
ruleName)` keys) treat that entry as **still owning its names**: a new file
claiming any of them is rejected with `held by inactive <key>`. The operator's
recourse is to update the inactive rule (re-`/addOrUpdate`) or `/delete` it before
reusing its names elsewhere. This keeps `/inactivate` reversible without ever
risking name collisions or accidental backend-data loss across rules that share a
metric name.

## Periodic scan

Every node runs the periodic scan independently, every 30 s by default
(configurable). The scan:

1. Reads every runtime-rule entry from management storage.
2. Diffs the entries against the in-memory state and classifies each difference.
3. Applies each delta. The main may update backend storage; peers update only their
   local in-memory state. Peers never write schema changes to the backend.
4. **Self-heals** any rule that has been paused by a peer for more than the
   self-heal threshold (60 s default) and whose underlying entry hasn't moved —
   this is the recovery path for a main that crashed mid-apply.
5. **Catches up** any rule whose paused state was missed because the cluster pause
   broadcast didn't arrive (RPC drop, partition). The peer re-applies the new
   content from the entry without waiting for a fresh pause.

The periodic scan is the only mechanism that closes the consistency bounds. Cluster
pause broadcasts and the live application path are optimisations on top — they make
healthy commits visible faster than 30 s — but the periodic scan is what guarantees
convergence will happen at all, even after pause RPCs are dropped, peers crash, or
the cluster topology flaps.

## Cluster model

- **Coordinator-agnostic.** Runs on any OAP cluster coordinator (Zookeeper /
  Kubernetes / Standalone / Etcd / Nacos) without adding a coordinator of its own.
- **Single main.** The lexicographically-first node in the sorted peer list is
  the main; every node computes this locally with no negotiation. Main changes
  only when the first node joins or leaves the cluster, which is rare.
- **Forwarding.** Calls to non-main nodes are forwarded to the main over the
  cluster bus; the operator gets the main's response transparently. A narrow
  fail-safe returns `HTTP 421 cluster_view_split` if a forwarded request arrives
  at a node whose own view also says it is not the main — this signals a
  transient peer-list disagreement, not a data problem.
- **Pause / resume broadcasts** are **best-effort**. They make the cluster
  converge in seconds rather than the 30 s scan window, but the system is correct
  even when every broadcast is lost — the periodic scan still converges within
  bounds.
- **No two-phase commit.** The on-demand workflow takes a single backend write
  (the entry upsert) as the cluster-wide commit point. Everything before it is
  reversible; everything after it eventually appears on every node.

### Concurrent same-file writes to different nodes

Under stable topology only one node is the main, so concurrent writes serialize
on the main's per-file lock — second write wins, both operators get an honest 200,
the cluster converges within one scan.

Under a brief topology flip where two nodes both believe they are the main, both
start their workflow locally and one detects the conflict via the pause broadcast
(the other side is already paused by `SELF`, not `PEER`). The detecting main
returns `HTTP 409 split_brain_detected` to its operator, broadcasts a resume, and
aborts; the surviving workflow runs to completion. **Even if detection misses the
window** and both operators get 200, the periodic scan resolves it: every node
re-reads the entry on the next scan and converges to whichever write reached
storage second. The 409 is an operator-feedback optimisation, not a correctness
gate — without it the system still converges.

## Schema-visibility fence (BanyanDB)

BanyanDB's distributed mode propagates registry writes from the meta-server to
every data node asynchronously. A naive flow — register the schema, immediately
resume dispatch — has a race: the registry holds the new measure but a data node
may not yet have caught up, so the first sample after the apply lands on an
unprepared node.

For runtime hot-updates this would mean the operator's `200 OK` could come back
before the cluster's data boundary actually moved. The runtime-rule install path
narrows the gap on a best-effort basis: every BanyanDB schema write returns an
etcd `mod_revision`, and the installer waits — synchronously, before resuming
dispatch, up to a bounded timeout (default 2s) — for every BanyanDB data node
to catch up to the highest revision the apply produced.

The visible contract for operators is:

- Between operator request and `200 OK`, all sample dispatch for the affected
  metric is paused on every node. In-flight samples are dropped (this is by
  design: a structural change means the schema is moving and in-flight data has
  no valid landing).
- When all data nodes confirm within the bounded window, the `200 OK` marks the
  moment the cluster's data boundary moves: samples written at or after the `200`
  use the new shape; samples written before use the old shape.
- When one or more nodes haven't applied within the window, OAP logs a warning
  naming the laggards and resumes dispatch anyway. The schema is already
  authoritative in etcd, so late nodes apply it asynchronously through their
  watcher — until they do, samples landing on those specific nodes for that
  metric may be rejected by the local data node briefly. This trades strict
  cluster-wide cutover for not wedging an apply behind a single slow node;
  operators who need strict behavior should fix the slow node, not loosen the
  timeout.

Elasticsearch and JDBC don't have multi-node schema fan-out; their storage change is
visible when the call returns, so the fence is a no-op for those backends.

## Failure handling — what operators see

The feature is designed so failures are visible without tailing logs and so the
recovery path is the same path operators already use.

- **Rule parse / compile error** — `HTTP 400 compile_failed` with the parser
  message. The entry was not persisted; this node and the cluster keep serving
  the prior rule for every metric.
- **Storage shape conflict the operator didn't acknowledge** — `HTTP 409
  storage_change_requires_explicit_approval`. No pause broadcast, no persist, no
  side effects. Re-push with `?allowStorageChange=true` if the change is
  intentional.
- **Backend storage verification failed mid-apply** — `HTTP 500 ddl_verify_failed`.
  Newly added metrics are rolled back so the backend doesn't accumulate orphans; the prior
  rule keeps serving every metric that wasn't being added or reshaped.
  `lastApplyError` on `/runtime/rule/list` carries the failure message.
- **Persist failed** — `HTTP 500 persist_failed`. Local state is rolled back to
  the pre-apply rule; peers self-heal within 60 s. The cluster never advanced
  past the failure.
- **Persist succeeded but the local finishing step failed** — `HTTP 500 commit_deferred`.
  Storage is authoritative (peers will converge), but this node will retry on
  its next periodic scan.
- **Cluster routing fail-safe** — `HTTP 421 cluster_view_split` when a forwarded
  request reaches a node that also doesn't believe it's the main. Wait for the
  peer-list to settle (seconds) and retry.

`GET /runtime/rule/list` is the canonical operator view of cluster state: persisted
status, per-node `localState`, and `lastApplyError` for any rule whose most recent
apply failed. There is no separate alert channel — `/list` plus the OAP log are
the entire diagnostic surface.

## What this feature does not do

- **OAL hot-update** is out of scope (see "Scope" above).
- **Authentication** is not built in. The admin endpoint is disabled by default;
  when enabled it must be gateway-protected. See the
  [API doc](../setup/backend/backend-runtime-rule-api.md) for setup guidance.
- **Bulk import.** `/dump` produces a tar.gz for backup, but restore is "extract
  one file, POST it to `/addOrUpdate`". There is no single-call cluster import.
- **Rule rollback.** Storage is last-write-wins; there is no automatic
  "previous version" history. Operators who need rollback should keep their
  rule YAMLs in version control and re-push the desired version through
  `/addOrUpdate`.
- **Across OAP-version clusters.** Different OAP binaries ship different static
  rule content; the runtime entries override consistently, but unoverridden static
  rules diverge along the version split. Use deployment discipline.

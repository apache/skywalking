# Runtime Rule Hot-Update — Architecture

Operators change MAL and LAL rule files at runtime without restarting OAP. Changes
persist in management storage, survive reboots, and propagate across every node in
an OAP cluster within a bounded window. This page explains the contract: what the
feature guarantees, how the cluster stays consistent, and what to expect when
something goes wrong. The HTTP surface is documented separately in
[Runtime Rule Hot-Update API](../setup/backend/admin-api/runtime-rule.md).

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

### Code-defined stream opt-in (narrow exception)

The "boot never reshapes" rule above applies to **runtime-rule (MAL / LAL)**
registration — those rules ride the `/addOrUpdate` REST workflow when their
backing schema needs to change.

Streams whose schema lives in OAP source code (e.g. `AlarmRecord`) can opt in
to **additive** boot-time reshape via
`@Stream(allowBootReshape = true)`. When the flag is on and the diff is
purely additive, the installer calls `client.update` at boot to extend the
live measure / stream; non-additive divergences still record
`SKIPPED_SHAPE_MISMATCH` and require an operator drop+recreate. Only the
init / standalone OAP performs the reshape; non-init peers continue through
the existing poll-and-wait loop so a single node drives DDL during a rolling
restart.

"Additive" includes two cases:

1. **New tag / new field** — a brand-new `@Column` is appended to the live
   tag family (or fields list, for measures).
2. **Tag relocation between families** — a `@Column`'s `storageOnly` flag
   flips, moving the tag between the `storage-only` and `searchable`
   families. The tag identity and type are preserved; only its on-disk
   family location changes.

Drops, tag-type changes, kind flips (tag↔field), and entity / interval /
sharding-key changes are still rejected with `SKIPPED_SHAPE_MISMATCH`.

When the primary `check*` records `SKIPPED_SHAPE_MISMATCH`, the dependent
`IndexRule` and `IndexRuleBinding` reconciliation is **also skipped**. This
preserves coherence between the stream / measure tag layout and the binding
that points into it — without the gate, the binding would silently update to
reference the new declared tag list while the live tag families still carry
the old shape, leaving operators with a binding routing to tags that don't
exist in the live family layout.

This opt-in is **BanyanDB-only**. JDBC and Elasticsearch are append-only on
the data path and already accept additive column / mapping additions at boot
without operator intervention, so the flag is unread on those backends.

> **Operator caveat:** BanyanDB does not physically migrate existing rows
> when a tag's family changes. Pre-existing data for the relocated tag stays
> in its original on-disk family location; new writes go to the declared
> family. Queries that route through a new IndexRule on the relocated tag
> will only see post-reshape rows until historical data ages out via TTL.

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
  changed). The main runs, in order:
  1. **Pause the cluster** — self-suspend first (so a concurrent peer apply is
     detected, not merged), then broadcast a pause to every peer over the cluster bus.
     Peers stop dispatching samples for the affected metrics and drain in-flight
     batches. Unreachable peers are logged and skipped; they self-recover via the
     periodic scan.
  2. **Fire the backend DDL on this node** — create / update the BanyanDB measures
     for the metrics this rule produces. This returns the schema revision but does
     **not** yet make the rule the cluster's truth.

  At this point the HTTP call returns `applyId` with phase `FENCING` — **accepted, not
  yet durable** — and the remaining steps run in the background so a slow data node
  never blocks the operator. The operator polls `GET /runtime/rule/status` (see the
  admin-API doc) to watch the rest:
  3. **Schema-visibility fence** — wait (up to a configurable budget, default 3 min)
     for every BanyanDB data node to apply the new measure schema (see below).
  4. **Persist the entry** — this is the cluster-wide commit point, and it happens
     **after** the fence, so a durable entry always implies "schema propagated
     cluster-wide".
  5. **Finalize + resume** — finalize the local commit (swap the bundle, reset alarm
     windows for any metric whose identity changed, unpark local dispatch) and
     resume / notify peers so they converge to the new entry. Peers that missed the
     notify self-heal within 60 s.

Because persist (step 4) is gated behind the fence (step 3), the ordering is
**pause → DDL → fence → persist → commit → resume**, and crash recovery is safe at
every point: a crash before persist leaves no entry — peers and the recovered main
stay on the old content (the orphaned measure from the DDL is inert) — and any entry
that *is* durable was fence-confirmed before it was written, so a peer converging to
it via the periodic scan never resumes dispatch against an unpropagated schema.

Outcomes (all observed by polling `/runtime/rule/status`, not by blocking the HTTP
call, which already returned at `FENCING`):

- **Pre-DDL error** (compile / verify) — phase `FAILED`; the entry was never advanced
  and the cluster keeps serving the prior rule. The HTTP call returns the error
  synchronously (it happens before the `FENCING` return).
- **Persist fails** — phase `FAILED`; the local node rolls back to the prior rule and
  resumes peers. The durable state never moved, so neither does the cluster.
- **Fence does not confirm within the budget** — phase `DEGRADED` with the lagging
  node ids; dispatch resumes anyway (a stuck node must not park the metric forever),
  and the schema converges through BanyanDB's own watcher.
- **Local finalize fails after persist** — phase `DEGRADED`: storage holds the new
  content and peers converge to it, but this node will retry the local finish on its
  next scan.

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
- **`/delete`** removes the runtime row. It refuses to operate on an `ACTIVE`
  row (returns `HTTP 409 requires_inactivate_first`), so destruction always goes
  through the explicit two-step `/inactivate → /delete` workflow. On an absent
  row it is an idempotent `200 not_found`. The `/inactivate` step has already
  torn down OAP-internal state under `withoutSchemaChange` (handlers, prototypes,
  Models cleared; backend measure preserved). What `/delete` does next depends on
  whether a bundled YAML twin exists on disk:
  - **No bundled twin (default mode)** — drops the row only; the backend measure
    (if any) is left in place as an inert artefact. This matches bundled-rule
    deletion semantics: removing a YAML from `otel-rules/` on disk doesn't drop
    its measure either. Operators who want backend cleanup must purge the
    measure out-of-band with the storage backend's tools.
  - **Bundled twin exists, default mode** — refused with `HTTP 409
    requires_revert_to_bundled`. Letting bundled silently take over the
    `(catalog, name)` after the row goes away is a meaningful state change that
    requires an explicit operator decision.
  - **Bundled twin exists, `?mode=revertToBundled`** — schema-change path.
    Bundled may have a different shape than runtime, so the apply pipeline runs
    a unified flow: re-install the prior runtime DSL locally under
    `withoutSchemaChange` (no backend touch), apply the bundled YAML through
    the standard `compile → fireSchemaChanges → verify → commit` pipeline with
    `withSchemaChange`. The commit's delta drops runtime-only metrics through
    the listener chain, registers bundled-only metrics, and reuses bundled-shared
    metrics at matching shape. The runtime row is then removed and the bundled
    rule is the active loader on this node. Peers converge via the periodic
    scan.
  - **No bundled twin, `?mode=revertToBundled`** — refused with `HTTP 400
    no_bundled_twin`.

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

BanyanDB's distributed mode propagates schema writes from the meta-server to every
data node asynchronously. A naive flow — register the schema, immediately resume
dispatch — has a race: the registry holds the new measure but a data node may not
yet have caught up, so a sample written before that node applies the schema is
**silently dropped** at the data node (not retried). The fence is therefore a
write-safety barrier, not just an observability check: it must gate dispatch resume.

Every BanyanDB schema write returns a `mod_revision`. After firing the DDL the main
waits — on a configurable budget (`deferredFenceTimeoutSeconds`, default 3 min) — for
every data node to catch up to the highest revision the apply produced, and only then
persists the rule entry, finalizes the local commit, and resumes dispatch. The wait
runs in the **background**: the HTTP call has already returned `applyId` at phase
`FENCING`, so a slow data node never blocks the operator, yet nothing durable or
visible happens until the schema is confirmed.

The contract for operators is:

- The HTTP call returns at `FENCING` (accepted, not yet durable). Dispatch for the
  affected metric stays paused on every node from the pause broadcast through the
  fence; this is a clean collection gap, not dropped writes (no node is writing the
  new shape yet). In-flight samples drained at pause are dropped by design — a
  structural change moves the schema and in-flight data has no valid landing.
- When all data nodes confirm within the budget, the entry is persisted and dispatch
  resumes — the cluster's data boundary moves at that moment. The status advances
  `FENCING → ROLLING_OUT → APPLIED`.
- When one or more nodes haven't applied within the budget, OAP logs a warning naming
  the laggards, persists + resumes **anyway**, and reports `DEGRADED` with the laggard
  ids. The schema is already authoritative, so late nodes apply it through their own
  watcher; until they do, samples landing on those specific nodes may be dropped
  briefly. This trades strict cluster-wide cutover for not wedging an apply behind a
  single slow node; operators who need strict behavior fix the slow node.

Because persist is gated behind the fence, the fence's guarantee survives a main
crash: a durable entry is always one whose schema was confirmed propagated, so a peer
converging to it (via the periodic scan, which performs no backend RPC of its own)
never resumes against an unpropagated schema. A crash before persist simply leaves no
entry, and the cluster stays on the prior, already-fenced content.

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
- **Backend storage verification failed mid-apply** — `HTTP 400 ddl_verify_failed`
  (this happens during the synchronous DDL step, before the `FENCING` return). Newly
  added metrics are rolled back so the backend doesn't accumulate orphans; the prior
  rule keeps serving every metric that wasn't being added or reshaped.
- **Cluster routing fail-safe** — `HTTP 421 cluster_view_split` when a forwarded
  request reaches a node that also doesn't believe it's the main. Wait for the
  peer-list to settle (seconds) and retry.

The errors above are returned synchronously, before the call returns `applyId` at
`FENCING`. The outcomes of the background tail (fence → persist → commit → resume) are
observed by polling `GET /runtime/rule/status?applyId=…`, not by an HTTP code:

- **Fence didn't confirm within the budget** — phase `DEGRADED` with the lagging node
  ids; dispatch resumed anyway, schema converges via the data nodes' watcher.
- **Persist failed** — phase `FAILED`; local state rolled back to the pre-apply rule,
  peers self-heal within 60 s. The cluster never advanced (no durable entry).
- **Local finishing step failed after persist** — phase `DEGRADED`; storage is
  authoritative (peers converge), this node retries on its next periodic scan.

`GET /runtime/rule/list` is the canonical operator view of cluster state (persisted
status, per-node `localState`, `lastApplyError`); `GET /runtime/rule/status` reports a
specific apply's live phase / laggards (and degrades to the durable entry when the
apply-id is gone). There is no separate alert channel — those two plus the OAP log are
the entire diagnostic surface.

## Dynamic layers

Runtime MAL/LAL rules MAY introduce new layer names through the same
`layerDefinitions:` block bundled rule files use:

```yaml
layerDefinitions:
  - name: MY_NEW_LAYER
    ordinal: 100050         # required, runtime tier (>=100_000)
    # normal: true          # optional; defaults to true
metricPrefix: my_prefix
metricsRules:
  - name: requests
    exp: any.sum(['service', 'instance'])
```

### Ordinal tier — operator-pinned

| Range                        | Channel                                                                |
|------------------------------|------------------------------------------------------------------------|
| `0 – 9_999`                  | Built-in `Layer.*` constants                                           |
| `10_000 – 99_999`            | `layer-extensions.yml` + bundled MAL/LAL `layerDefinitions:`           |
| `100_000 – Integer.MAX_VALUE`| Runtime DSL dynamic layers                                             |

The OAP does NOT auto-allocate. Ordinals land in persisted `ServiceTraffic` primary
keys; they must be operator-stable across restarts.

### Lifecycle

`RuntimeLayerRegistry` refcounts each runtime claim per declaring rule. When the last
runtime claim is removed (`/delete` of the rule, or `/addOrUpdate` that drops the
entry), the layer is unregistered if it was originally registered through the runtime
channel.

### Limitations

- **Runtime overrides of bundled rules cannot declare `layerDefinitions:`**. Bundled
  and runtime are separate layer-ownership channels — never overwrite, never share. The
  REST handler rejects `/addOrUpdate` of a rule whose name matches a bundled disk file
  AND whose body carries a non-empty `layerDefinitions:` block with `applyStatus =
  layer_override_forbidden` (HTTP 400). Operators wanting new layers on a bundled rule
  must either edit the bundled source on disk and restart, OR push a pure-runtime rule
  with a different name that declares the layer.
- **Legacy override rows** (persisted before the rejection was introduced) are handled
  at boot: the static-loader substitution is dropped so the bundled disk content loads
  with its original layers, and `RuleSync` then applies the runtime row dynamically
  post-seal — the runtime row's `layerDefinitions` go through the dynamic channel and
  are operator-removable via `/inactivate` + `/delete`.
- **Pure runtime rules (no bundled twin) ARE fully removable**. The layer registers
  through the runtime channel and `unregisterDynamic` succeeds when the refcount hits
  zero.
- **Removing a layer with historical `ServiceTraffic` rows orphans those rows** — the
  persisted ordinal no longer resolves and reads throw `Unknown Layer value`. Operator
  must migrate or purge the data before removal; the feature does not auto-clean.
- **No redeclaration of built-in or boot-time-external layers** at runtime. Use the
  existing name in your rule body (`layer: GENERAL`, `dest: ...Layer.MESH...`) — do
  not list those names under `layerDefinitions:`.

### Conflict rules

Validation runs before any apply lands and returns HTTP 400 with the standard
`{applyStatus, catalog, name, message}` envelope.

| `applyStatus`                | Trigger                                                                                                    |
|------------------------------|------------------------------------------------------------------------------------------------------------|
| `layer_ordinal_out_of_range` | `ordinal:` missing (default 0) or `< 100_000`.                                                             |
| `layer_name_invalid`         | `name` does not match `[A-Z][A-Z0-9_]*`.                                                                   |
| `layer_name_conflict`        | Same `name` already registered with a different `(ordinal, normal)` by another rule (bundled or runtime). Also raised for same-batch duplicate names with different triples, and for self-edit triple changes when another runtime rule shares the layer. |
| `layer_ordinal_collision`    | Different `name`, same `ordinal` already in use. Also raised for same-batch duplicate ordinals, and for self-edit ordinal reuse when another runtime rule still holds the prior layer at that ordinal. |
| `layer_override_forbidden`   | `/addOrUpdate` on a rule with a bundled disk twin where the body contains `layerDefinitions:`. Bundled rules own their layer declarations; runtime overrides may only change the rule body. |

Same triple as an existing bundled layer is permitted as a soft claim (see Limitations
above). Same triple as an existing runtime layer is a no-op refcount-add. Self-edits
(changing the triple of a layer this rule already owns) are permitted only when the
rule is the sole claimant; if other rules share the name, the operator must align both
sides in lockstep or remove the prior declaration first.

INACTIVE rules hold no layer claims — their refcount entries are dropped at
`/inactivate`. Reactivation (`/addOrUpdate` against an INACTIVE row) runs full
validation; if another rule started claiming the name in the meantime,
`layer_name_conflict` surfaces and the operator must edit or `/delete` the inactive
rule before it can come back.

## What this feature does not do

- **OAL hot-update** is out of scope (see "Scope" above).
- **Authentication** is not built in. The admin endpoint is disabled by default;
  when enabled it must be gateway-protected. See the
  [API doc](../setup/backend/admin-api/runtime-rule.md) for setup guidance.
- **Bulk import.** `/dump` produces a tar.gz for backup, but restore is "extract
  one file, POST it to `/addOrUpdate`". There is no single-call cluster import.
- **Rule rollback.** Storage is last-write-wins; there is no automatic
  "previous version" history. Operators who need rollback should keep their
  rule YAMLs in version control and re-push the desired version through
  `/addOrUpdate`.
- **Across OAP-version clusters.** Different OAP binaries ship different static
  rule content; the runtime entries override consistently, but unoverridden static
  rules diverge along the version split. Use deployment discipline.

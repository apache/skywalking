# Runtime Rule Hot-Update API

The runtime rule receiver plugin lets operators add, override, inactivate, and delete
MAL and LAL rule files at runtime without restarting OAP. Changes are saved in the
configured storage backend (JDBC, Elasticsearch, or BanyanDB) and propagate across
every node in an OAP cluster within 30 s by default.

> For the consistency contract, the three workflows (boot / on-demand / periodic scan),
> the lifecycle, and how cluster failures are handled, see
> [Runtime Rule Hot-Update — Architecture](../../concepts-and-designs/runtime-rule-hot-update.md).
> This page focuses on the REST API surface.

## ⚠️ Security notice

The admin port has **no authentication** in this release. The module is therefore
**disabled by default**; enabling it opens an HTTP endpoint that can change metric and
log-processing rules while OAP is running.

Operators enabling the module MUST:

1. Gateway-protect the port with an IP allow-list and separate authentication rules.
2. Never expose port 17128 to the public internet.
3. Bind the HTTP server to `localhost` or a private network interface if remote access is
   not required.

## Enabling the module

Set the selector to `default` in `application.yml` or via env var:

```bash
SW_RECEIVER_RUNTIME_RULE=default
```

Default port is `17128`. All config knobs are in `application.yml` under the
`receiver-runtime-rule` block — host, port, periodic-scan interval, self-heal threshold.

## HTTP surface

`/addOrUpdate` takes the rule file as the raw request body and identifies the rule with
the `catalog` and `name` query parameters. `/inactivate` and `/delete` use the same
parameters with an empty body. There is no JSON request envelope, so shell scripts can
send a YAML file directly with `--data-binary @file.yaml`.

### Content encoding

Rule content is **UTF-8 YAML text**. The API never base64-encodes content.

- Raw responses (`Content-Type: application/x-yaml; charset=utf-8`) and raw
  `/addOrUpdate` request bodies carry content byte-identically.
- JSON responses encode content as a **standard JSON string** — special characters are
  JSON-escaped (`"`, `\`, control characters become `\u00XX`, newlines become `\n`). A
  standard JSON parser yields the original UTF-8 YAML; no additional decode step.

Example JSON response from `GET /runtime/rule?catalog=otel-rules&name=vm` with
`Accept: application/json`:

```json
{
  "catalog": "otel-rules",
  "name": "vm",
  "status": "ACTIVE",
  "source": "runtime",
  "contentHash": "5f9b8d3e...",
  "updateTime": 1714102400000,
  "content": "expSuffix: instance(['host_name','host_ip'], Layer.OS_LINUX)\nmetricPrefix: meter_vm\nmetricsRules:\n  - name: cpu_total_percentage\n    exp: avg_node_cpu_utilization\n"
}
```

The `\n` in the `content` string is the JSON escape for newline. After `JSON.parse()`
the value is the YAML body that `/addOrUpdate` would accept verbatim.

`contentHash` is the SHA-256 of the UTF-8 content bytes (lowercase hex). It is identical
across raw and JSON modes; the JSON envelope's escaping does not affect the hash. The
same hash appears on `GET /runtime/rule/list`, where `localState` shows whether this node
has already applied that stored version.

`/addOrUpdate` decodes the request body as UTF-8 regardless of the `Content-Type` header.
Send valid UTF-8 YAML. If the decoded content cannot be parsed or compiled as a rule, the
server returns `400 compile_failed`.

### Canonical routes

**Write endpoints**

| Method | Path                                                                               | Body          | Effect |
|--------|------------------------------------------------------------------------------------|---------------|--------|
| POST   | `/runtime/rule/addOrUpdate?catalog=&name=[&allowStorageChange=true][&force=true]` | raw rule YAML | Creates or replaces a rule. Edits that keep the same metric storage shape are applied without pausing the cluster. Edits that add, remove, or reshape metrics pause affected traffic, update and verify backend storage, save the rule, and then resume. If the posted content exactly matches the current `ACTIVE` rule, the server returns `no_change`; `force=true` skips that shortcut for recovery. |
| POST   | `/runtime/rule/inactivate?catalog=&name=`                                          | empty         | Soft-pauses a rule. OAP stops using the rule and saves it as `INACTIVE`, while the backend measure and historical data remain available for reactivation. |
| POST   | `/runtime/rule/delete?catalog=&name=[&mode=revertToBundled]`                       | empty         | Removes an `INACTIVE` runtime row. Active rules return `409 requires_inactivate_first`. **No bundled twin, default mode** → drops the row; the backend measure (if any) stays as an inert artefact. **Bundled twin exists, default mode** → returns `409 requires_revert_to_bundled` because letting bundled silently take over the `(catalog, name)` is a meaningful state change requiring an explicit operator decision. **Bundled twin exists, `?mode=revertToBundled`** → schema-change path: the orchestrator rehydrates the runtime DSL locally and runs the bundled YAML through the standard apply pipeline so the runtime→bundled delta drops runtime-only metrics, registers bundled-only metrics, and reuses bundled-shared metrics at matching shape; the row is then removed and the bundled rule is the active loader on the local node. Peers converge via the gone-keys reconcile path on their next tick. **No bundled twin, `?mode=revertToBundled`** → returns `400 no_bundled_twin`. |

**Read endpoints**

| Method | Path                                                                                | Effect                                                                      |
|--------|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| GET    | `/runtime/rule?catalog=&name=`                       | Fetches one rule. Runtime rule first, bundled rule second, otherwise 404. Raw YAML by default; JSON envelope on `Accept: application/json`. Supports `ETag` and `If-None-Match`. |
| GET    | `/runtime/rule/bundled?catalog=&withContent=false`   | Returns bundled rules for one catalog as JSON. `withContent` defaults to true; `false` omits each YAML body. Each item includes whether an operator override exists. |
| GET    | `/runtime/rule/list[?catalog=]`                      | Returns a single JSON envelope `{generatedAt, loaderStats, rules}` merged from stored rules and this node's local state. Each row carries `loaderKind`, `loaderName`, `bundled`, and `bundledContentHash` so a UI can render override badges without a second roundtrip. Optional `catalog=` narrows the output; unknown values return `400 invalid_catalog`. |
| GET    | `/runtime/rule/dump[/<catalog>]`                     | Downloads a tar.gz of stored runtime rules plus `manifest.yaml`. The server has no bulk import endpoint; the CLI restore command replays individual `addOrUpdate` and `inactivate` calls. |

### `/delete` storage semantics — per backend

`/delete` tears down the runtime DSL on the OAP side (the `/inactivate` step that must
precede it has already unparked dispatchers, removed workers, and dropped the model from
the in-memory registry) and removes the stored rule from `/runtime/rule/list`. What
happens to the **backend schema and data** depends on the path taken:

| Path | Backend effect |
|---|---|
| **Default mode, no bundled twin** | The runtime DSL was already torn down by `/inactivate`. The backend measure (if any) is left in place as an inert artefact — no listener writes to it, but the schema and historical rows stay. This matches bundled-rule deletion semantics on disk: removing a YAML from `otel-rules/` doesn't drop its measure either. Reclaim manually via the storage backend's tools if you need the schema gone. |
| **Default mode, bundled twin** | Refused with `409 requires_revert_to_bundled`. The operator must opt in explicitly. |
| **`?mode=revertToBundled`, bundled twin** | Schema-change path. Bundled may have a different shape than runtime. The runtime DSL is rehydrated locally so the apply pipeline can compute the runtime→bundled delta. Metrics in the delta:<br>• **Runtime-only** (in runtime, not in bundled) — dropped via the listener chain. On BanyanDB the measure / stream is dropped. On ES / JDBC `dropTable` is a documented no-op (their tables are append-only; TTL reclaims space).<br>• **Bundled-only** (in bundled, not in runtime) — created.<br>• **Bundled-shared at matching shape** — reused; no schema mutation.<br>• **Bundled-shared at differing shape** — reshaped via the listener chain (additive subset each backend supports online: `client.update` for BanyanDB, add-column for JDBC, mapping append for ES). |
| **`?mode=revertToBundled`, no bundled twin** | Refused with `400 no_bundled_twin`. |

Historical query semantics on ES and JDBC are unchanged from prior releases: tables stay
beyond `dropTable` and TTL reclaims rows.

A re-`addOrUpdate` of the same rule (same name, scope and downsampling) replays
schema registration. On BanyanDB this re-creates the measure; on ES / JDBC this is a
no-op against the existing index / table. In both cases new samples land alongside any
retained history.

### Catalog shortcut routes

Implicit catalog in the path — useful when scripting against a single catalog:

- `/runtime/mal/otel/{addOrUpdate,inactivate,delete}` → `catalog=otel-rules`
- `/runtime/mal/log/{addOrUpdate,inactivate,delete}` → `catalog=log-mal-rules`
- `/runtime/lal/{addOrUpdate,inactivate,delete}` → `catalog=lal`

`telegraf-rules` is supported by the canonical `/runtime/rule/...` routes; it does not
currently have a shortcut route.

### Valid catalogs + names

| Catalog | What it holds |
|---|---|
| `otel-rules` | OTEL MAL rule YAML files |
| `log-mal-rules` | Log-derived MAL rule YAML files |
| `telegraf-rules` | Telegraf MAL rule YAML files |
| `lal` | LAL rule YAML files |

Rule `name` mirrors the static filesystem layout — a relative path under the catalog root
without extension. Segments match `[A-Za-z0-9._-]+`, joined by `/`. No leading slash,
no `..`, no empty segments, no backslash. Examples: `nginx`, `aws-gateway/gateway-service`,
`k8s/node`.

### `allowStorageChange` parameter

`/addOrUpdate` (and the three catalog shortcut variants) accept an optional
`allowStorageChange` query parameter. Default is **false** when absent.

The server rejects any update that would move storage identity unless this flag is set:

- **MAL**: scope type change (`service(...)` → `instance(...)`), explicit downsampling
  function change (`.downsampling(SUM)` → `.downsampling(MAX)`), switching between single /
  labeled / histogram variants.
- **LAL**: changing `outputType` on any rule, adding or removing a rule key within a file.

These are the edits that drop an existing BanyanDB measure's data, change how new samples
are stored, or leave old rows behind on JDBC / Elasticsearch. Body, filter, and tag tweaks
that preserve each metric's storage identity are always accepted and do not reset alarm
windows.

Accepted truthy forms (case-insensitive): `true`, `1`, `yes`. Anything else is treated as
false.

> **Recommendation — avoid storage-wipe edits in production.** Passing
> `allowStorageChange=true` drops the existing measure's data on BanyanDB and orphans the
> old rows on JDBC / Elasticsearch; any alarm rules, dashboards, and historical queries
> that reference the old shape will miss the pre-change window. Unless the data loss is
> understood and intended — typically only on a staging cluster or during a planned
> schema migration — leave the flag off. Prefer a rename (new metric name, new rule
> name) so the old data keeps accumulating until TTL and the new data starts fresh under
> a clean identity. Treat the flag as an explicit "I accept data loss" affirmation, not a
> convenience toggle.

> **Edge case — `/addOrUpdate` after a `/delete` of a runtime-only rule.** Default `/delete`
> with no bundled twin leaves the backend measure in place as an inert artefact. A later
> `/addOrUpdate` against the same `(catalog, name)` has no `priorContent` to diff against,
> so the storage-change guardrail will not refuse the request even when the new content
> reuses the same metric names with a different shape. The apply pipeline's listener chain
> may reshape the inert backend measure silently. If you suspect a stale schema from a
> previously-deleted rule, push the new rule with `allowStorageChange=true` so the intent
> is explicit; or rename the metrics in the new rule so the old schema stays inert and a
> fresh measure is created instead.

### Recovery from a failed apply

When an `/addOrUpdate` fails during validation or apply, the node does **not** lose the
previous rule version. The pre-change rule keeps serving every metric that was not being
changed, and the response includes an `applyStatus` explaining the failure.

**What to expect during a failure:**

- The node keeps serving the prior rule for **unchanged** metrics. Samples continue
  flowing to the existing measures; dashboards and alarm rules against those metrics keep
  working.
- Metrics that were **newly added** by the failed attempt are rolled back (no orphan
  measures left on BanyanDB).
- Metrics in the **storage-changing** set — where the rule changed a metric's function or scope
  and was allowed through with `allowStorageChange=true` — are lost. The old measure was
  removed before the new one attempted registration; a mid-flight failure leaves neither.
  This is the documented cost of `allowStorageChange`.
- `/runtime/rule/list` reports the rule's `lastApplyError` so the failure is visible
  without tailing logs.
- Severe backend or apply failures also write an `ERROR` log line naming the catalog, rule,
  and reason.
- Peers either self-heal back to running on the old content (if the row was never
  committed) or retry the same broken content on their next periodic scan and fail the same
  way (if the row did advance). Either way they never serve samples against a moved schema
  while the main's apply was in flight.

**Recovery flow:**

1. **Inspect.** `curl /runtime/rule/list | jq 'select(.lastApplyError != null)'`. Confirm
   which rule is degraded and read the error message.
2. **Diagnose.** Check the OAP log when the list output is not enough. Typical causes:
   - Rule syntax or parse error — fix the YAML and re-push via `/addOrUpdate`.
   - Storage schema moved without the guardrail — re-push with
     `?allowStorageChange=true&force=true` (see below), or rename the metric so the old
     measure keeps accumulating until TTL and new data flows under a new identity.
   - Backend unavailable during a schema update — retry once the backend is healthy; the
     next periodic scan will also retry without operator action.
3. **Apply the fix.** Two options:

   **Option A: re-push via `/runtime/rule/addOrUpdate` with the recovery flags.**

   ```bash
   curl -X POST --data-binary @vm-previous-known-good.yaml \
     "http://OAP:17128/runtime/rule/addOrUpdate?catalog=otel-rules&name=vm&allowStorageChange=true&force=true"
   ```

   Two flags layer on top of the regular addOrUpdate:
   - `allowStorageChange=true` — accepts shape-breaking edits the guardrail would otherwise
     reject with 409.
   - `force=true` — bypasses the same-content `no_change` HTTP shortcut so a re-post of
     known-good bytes is treated as a fresh apply request. The persisted row (if any) is
     re-written and any peers stuck mid-Suspend are re-Resumed; **schema and dispatch
     handlers are not rebuilt** — the in-memory engine state is content-keyed, so a true
     no-op against a healthy node remains a no-op even with `force=true`. This is the
     unstick path for "the prior push raced a transient backend failure and a peer is
     still suspended"; if you need the engine to recompile (e.g., after manually editing
     a backend table), re-post the content with a single character change first, then the
     real content.
   Combine both when the recovery target re-shapes the measure. Same failure modes as a
   normal `/addOrUpdate` — bad rule content still fails `400 compile_failed` and the prior rule
   keeps serving.

   **Option B: Manual restore from a prior `/dump` tarball.** If you have a dump taken
   before the broken push, extract the specific file and re-post it with the recovery
   flags:

   ```bash
   # assuming runtime-rule-dump-2026-04-22.tar.gz was taken before the broken push.
   # Archive entries are under runtime-rule-dump/<catalog>/<name>.yaml.
   tar -xzf runtime-rule-dump-2026-04-22.tar.gz runtime-rule-dump/otel-rules/vm.yaml
   curl -X POST --data-binary @runtime-rule-dump/otel-rules/vm.yaml \
     "http://OAP:17128/runtime/rule/addOrUpdate?catalog=otel-rules&name=vm&allowStorageChange=true&force=true"
   ```

4. **Verify.** Re-run `list` and confirm `lastApplyError` is cleared and `localState` is
   `RUNNING`. Watch the OAP log for the apply-OK confirmation.
5. **(Best practice)** Take a fresh `/runtime/rule/dump` immediately after a successful
   recovery so the new baseline is captured for any future incident.

**What the recovery flags do NOT do:**

- They do not roll the rule content back to a previous version automatically. Runtime-rule
  storage is last-write-wins; `/addOrUpdate` (with or without `force=true`) is a write
  path, not a rollback path. The operator supplies the content to restore.
- They do not bypass rule compile errors. If the content is syntactically invalid, the node
  returns `400 compile_failed` whether or not `force=true` is set. The flags accept
  storage-level changes the guardrail would block and re-drive a stuck same-content
  shortcut; they do not accept broken rule content.

### Response codes

Write endpoints return JSON: `{applyStatus, catalog, name, message}`. Read endpoints use
the response formats listed above; their error responses use the same JSON shape.

**Success**

| Status        | `applyStatus`              | Meaning                                                                                                |
|---------------|----------------------------|--------------------------------------------------------------------------------------------------------|
| 200 OK        | `no_change`                | content byte-identical to current row; nothing to do                                                   |
| 200 OK        | `filter_only_applied`      | body / filter edits applied via fast path; no backend storage change, no alarm reset                   |
| 200 OK        | `structural_applied`       | storage-changing edit applied: cluster pause, backend update and check, persist, cluster resume all succeeded |
| 200 OK        | `inactivated`              | row flipped to `INACTIVE`; backend measure and data preserved                                          |
| 200 OK        | `static_tombstoned`        | `/inactivate` against a rule that exists only on disk; an `INACTIVE` tombstone row is now persisted    |
| 200 OK        | `already_inactive`         | `/inactivate` against an already-inactive row; idempotent no-op                                        |
| 200 OK        | `deleted`                  | `/delete` of a rule with no bundled twin; row removed, backend measure left as inert artefact          |
| 200 OK        | `reverted_to_bundled`      | `/delete?mode=revertToBundled`; runtime row removed, bundled rule installed via the apply pipeline (schema change handled by the standard delta path) |
| 200 OK        | `not_found`                | `/inactivate` or `/delete` against an absent rule; idempotent no-op                                    |
| 200 OK        | `filter_only_persisted`    | row persisted but the in-memory swap threw on this node; converges on the next periodic scan          |

**Client error — caller has to act**

| Status            | `applyStatus`                                 | Meaning                                                                                                                |
|-------------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| 400 Bad Request   | `compile_failed`, `empty_body`, `invalid_*`   | rule parse failure or request validation failure; row was NOT persisted                                                |
| 400 Bad Request   | `invalid_catalog`, `invalid_mode`             | unknown `catalog=` or `mode=` query value                                                                              |
| 400 Bad Request   | `no_bundled_twin`                             | `/delete?mode=revertToBundled` against a rule with no bundled YAML on disk; drop the mode flag, or check that the bundled YAML exists |
| 409 Conflict      | `storage_change_requires_explicit_approval`   | update would move storage identity and `allowStorageChange` was not set — no cluster pause, no persist, no side effects |
| 409 Conflict      | `update_in_progress`                          | another apply is already in flight for this rule; retry after a few seconds                                            |
| 409 Conflict      | `requires_inactivate_first`                   | `/delete` against an `ACTIVE` row; run `/inactivate` first, then `/delete`                                             |
| 409 Conflict      | `requires_revert_to_bundled`                  | `/delete` (default mode) against a rule with a bundled YAML twin on disk; either re-issue with `?mode=revertToBundled` to fall back to bundled, or leave the row `INACTIVE` |
| 409 Conflict      | `delete_refused`                              | cross-file ownership conflict: bundled's claims overlap another active bundle. Update or `/inactivate` the conflicting bundle(s) first |
| 503 Service Unavailable | `storage_unavailable`                    | storage could not be read while checking the current rule; retry when storage is healthy                               |

**Cluster-routing errors — usually transient**

| Status                         | `applyStatus`            | Meaning                                                                                                                  |
|--------------------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------------|
| 409 Conflict                   | `origin_conflict`        | a peer rejected the cluster pause because it was already running its own apply (split-brain); the loser aborts and resumes |
| 409 Conflict                   | `split_brain_detected`   | this node detected a competing main during the cluster pause; aborted and broadcast a resume                              |
| 421 Misdirected Request        | `cluster_view_split`     | the receiving node's peer-list disagreed with the sender's; refused to re-forward. Wait a few seconds for the peer-list to settle, then retry |
| 502 Bad Gateway                | `forward_failed`         | could not reach the cluster main to forward the request; transport error message in `message`                            |

**Server error — apply or persist failed**

| Status                    | `applyStatus`                  | Meaning                                                                                                                     |
|---------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| 500 Internal Server Error | `ddl_verify_failed`            | backend storage was changed but the post-apply check rejected the new shape; new metrics rolled back, prior rule preserved |
| 500 Internal Server Error | `apply_failed`                 | server failed while applying the rule; partial changes rolled back, prior rule preserved                                   |
| 500 Internal Server Error | `persist_failed`               | row write failed; on filter-only this node still serves the pre-edit rule, on structural the local node rolled back and resumed peers |
| 500 Internal Server Error | `commit_deferred`              | apply succeeded and row was persisted, but the local finishing step failed on this node. Storage is authoritative and peers will converge; this node will retry on its next periodic scan |
| 500 Internal Server Error | `teardown_deferred`            | row was inactivated, but local cleanup failed; this node retries on the next periodic scan                                  |
| 500 Internal Server Error | `revert_to_bundled_failed`     | bundled apply failed during DDL or verify (typically a backend-storage issue — BanyanDB unreachable, shape rejection, or schema-barrier timeout). The orchestrator unwound the step-1 runtime install so local state matches the persisted INACTIVE row. Retry once storage recovers. |
| 500 Internal Server Error | `revert_to_bundled_precondition_failed` | revertToBundled prep step failed (no engine for catalog, MeterSystem unavailable for installRuntime). Local state is unchanged. Retry when the prerequisite recovers. |
| 500 Internal Server Error | `dao_unavailable`, `inactivate_failed`, `delete_failed`, other `*_failed` | management storage or local cleanup failed; check the message for the specific failure point. |

## Per-node list output

`GET /runtime/rule/list` returns a single JSON envelope:

```json
{
  "generatedAt": 1730000000000,
  "loaderStats": { "active": 27, "pending": 0 },
  "rules": [
    {
      "catalog": "otel-rules",
      "name": "vm",
      "status": "ACTIVE",
      "localState": "RUNNING",
      "suspendOrigin": "NONE",
      "loaderGc": "LIVE",
      "loaderKind": "RUNTIME",
      "loaderName": "runtime-rule:otel-rules/vm@0428-153042",
      "contentHash": "7c3a…",
      "bundled": true,
      "bundledContentHash": "c3d4…",
      "updateTime": 1730000000000,
      "lastApplyError": ""
    }
  ]
}
```

Bundled-only rows (no operator override) and recently deleted rows omit fields that
do not exist in storage, such as `updateTime` and `lastApplyError`. UI clients call
`fetch().json()` once; operators can `jq '.rules[]'` for line-oriented inspection.

### Rule status by source (bundled vs. runtime)

The combination of `status`, `loaderKind`, and `bundled` tells you which copy of a rule
the OAP is actually serving on this node. Reading these three fields together:

| Operator action history | `status` | `loaderKind` | `bundled` | What is serving |
|---|---|---|---|---|
| Bundled rule shipped on disk; operator never touched it | `BUNDLED` | `NONE` | `true` | Bundled YAML, served from the OAP's shared default classloader (registered at boot by the catalog loaders). |
| Operator pushed `/addOrUpdate` overriding a bundled rule | `ACTIVE` | `RUNTIME` | `true` | Runtime override in a per-file `runtime-rule:` loader. Compare `contentHash` with `bundledContentHash` to detect drift. |
| Operator pushed `/addOrUpdate` for a brand-new rule (no bundled twin) | `ACTIVE` | `RUNTIME` | `false` | Runtime override in a per-file `runtime-rule:` loader. No bundled fallback. |
| Operator `/inactivate`d a runtime override of a bundled rule | `INACTIVE` | `NONE` | `true` | Nothing — handlers are unregistered. The bundled rule does **not** auto-resurrect; to turn it back on, push `/addOrUpdate` (with the bundled YAML or your own) or call `/delete?mode=revertToBundled` (which reverts to bundled via the schema-change path). Plain `/delete` is refused with `409 requires_revert_to_bundled` to force the explicit decision. |
| Operator `/inactivate`d a bundled-only rule | `INACTIVE` | `NONE` | `true` | Nothing — same as above. The `INACTIVE` row is a tombstone carrying the bundled YAML at inactivate-time. |
| Operator `/inactivate`d a brand-new runtime rule | `INACTIVE` | `NONE` | `false` | Nothing — handlers gone. To turn back on: `/addOrUpdate` (with new content) or `/delete` (rule is fully gone). |
| `/delete?mode=revertToBundled` propagating after a bundled-twin row was removed | `n/a` (no row) | `BUNDLED` | `true` | Bundled rule, freshly compiled into a `bundled:` loader. Equivalent to a fresh boot of bundled. |

Quick decision rules for an operator reading `/list`:

- `status=BUNDLED` → comes from disk only.
- `status=ACTIVE` + `bundled=true` + `contentHash != bundledContentHash` → runtime override is *modified* relative to bundled. UIs typically render this as "Override (modified)".
- `status=ACTIVE` + `bundled=true` + `contentHash == bundledContentHash` → runtime override matches bundled. UIs typically render this as "Override (matches bundled)" — common after an explicit `/addOrUpdate ?source=bundled` revert.
- `status=ACTIVE` + `bundled=false` → runtime-only rule, no on-disk twin.
- `status=INACTIVE` → soft-paused. The DAO row preserves the content the operator last had; `/list` does not surface it (call `GET /runtime/rule` for the YAML).
- `loaderKind=BUNDLED` → a `bundled:` loader is currently serving (typical after `/delete?mode=revertToBundled`, where the bundled YAML was compiled into a fresh per-file loader).
- `loaderKind=NONE` → no per-file loader. For `BUNDLED` this is normal (shared default loader). For `INACTIVE` this is the rule being off.

- `status` — `ACTIVE` or `INACTIVE` for stored rows. `BUNDLED` and `n/a` are synthesized
  list values:
  - `BUNDLED` — shipped on disk, no operator override. Healthy steady state; no runtime
    row exists.
  - `n/a` — transient: a runtime row was just removed and this node hasn't swept it yet.
    Cleared on the next periodic scan.
- `localState` — per-node transient: `RUNNING` | `SUSPENDED` | `NOT_LOADED`. Distinct from
  `status`; a node mid-structural-apply is `ACTIVE` + `SUSPENDED`. After `/inactivate`,
  `localState` is `NOT_LOADED` regardless of whether a bundled twin exists on disk —
  `/inactivate` is a soft-pause that respects the operator's "off" intent. Bundled
  fall-over only fires on `/delete` (default mode for a bundled-twin row) or the gone-keys
  reconcile path.
- `suspendOrigin` — when `localState=SUSPENDED`, who paused this node:
  - `SELF` — this node is running its own apply.
  - `PEER` — the cluster main paused this node for its apply.
  - `BOTH` — should not appear under correct routing; presence signals a transient
    split-brain that clears via the normal handshake or the 60 s self-heal.
- `loaderGc` — diagnostic indicator showing whether the per-rule isolation has been retired
  for this rule and (if so) whether the JVM has reclaimed it. Operators normally don't
  need to act on this; a value other than `LIVE` for an `ACTIVE` row would suggest a
  rule cleanup issue worth investigating.
- `loaderKind` — origin of the per-file class loader currently serving this rule:
  - `RUNTIME` — operator-pushed runtime override.
  - `BUNDLED` — bundled rule serving via bundled fall-over (a runtime override was previously
    in place, then `/delete?mode=revertToBundled` reinstalled the bundled YAML in a fresh
    `bundled:` loader).
  - `NONE` — no per-file loader (typical for bundled-only rules served from the shared
    default loader; also a row whose loader has been retired but not yet replaced).
- `loaderName` — formatted loader name (`<kind>:<catalog>/<rule>@<MMdd-HHmmss>`), the same
  string the JVM surfaces in stack traces and the loader graveyard's INFO/WARN log lines.
  Empty when `loaderKind` is `NONE`.
- `contentHash` — SHA-256 of the stored content for runtime rows, or the local content for
  bundled-only and recently deleted rows. Matching hashes plus `localState=RUNNING` mean two
  nodes are serving the same content for that rule.
- `bundled` — `true` when a bundled YAML exists on disk for `(catalog, name)`. Set on every
  row regardless of status, so a UI can render an "Override" / "Modified from bundled"
  badge by comparing `contentHash` with `bundledContentHash`.
- `bundledContentHash` — SHA-256 of the bundled YAML, present only when `bundled=true`.
  A diff between `contentHash` and `bundledContentHash` indicates a runtime override that
  has drifted from the bundled rule.
- `lastApplyError` — most recent local apply error. Empty when the last apply succeeded,
  no attempt has been made, or the rule was inactivated (the inactive path clears stale
  errors so `/list` doesn't surface an error against a rule that is already down).
- `pendingUnregister` — only set for `status=n/a` entries; the row was just deleted and
  teardown is scheduled for the next periodic scan.

The `loaderStats` envelope counter exposes process-wide DSL classloader bookkeeping —
`active` is the number of rules currently served by per-file loaders, `pending` is the
number of retired loaders the JVM has not yet collected. A steadily elevated `pending`
across many polls is the leak signal the OAP also surfaces as a WARN log line.

### Reading a single rule's content — `GET /runtime/rule`

`GET /runtime/rule?catalog=…&name=…` returns the YAML body for one rule. By default
(`source=runtime` or omitted) the runtime row wins — bundled YAML is returned only when no
runtime row exists. Pass `?source=bundled` to read the bundled YAML even when a runtime
override is in place; the response 404s with `not_found` when the rule has no bundled twin.

This makes the "compare runtime override against bundled" workflow a two-call sequence:
fetch the runtime body with the default request, then fetch the bundled body with
`?source=bundled` and diff in the editor. `POST /runtime/rule/delete` drops the runtime
override; the next `/list` will show the row served by the bundled fall-over
(`loaderKind=BUNDLED`).

## Consistency model — at a glance

The full contract is in the
[architecture doc](../../concepts-and-designs/runtime-rule-hot-update.md#the-consistency-contract).
The headline:

- **Persist is commit.** Once `/addOrUpdate` returns 200, the cluster will converge on
  that content.
- **Last write wins.** Concurrent writes to different nodes serialize on the cluster main;
  the second write wins. The losing operator gets `409 split_brain_detected` if the cluster
  detected the race; otherwise both operators see 200 and the second commit's content is
  what every node ends up running.
- **Bounded convergence.** Healthy structural commits land cluster-wide within 30 s
  (one periodic scan). Aborted commits self-heal within 60 s. Filter-only edits land
  locally in milliseconds and on every other node within 30 s.
- **No quorum, no leader election, no two-phase commit.** The runtime-rule entry in
  storage is the single source of truth.
- **Samples for an affected metric are dropped during a structural cutover.** This is by
  design — the schema is moving and in-flight samples have no valid landing.

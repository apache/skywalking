# AI Agent Conversations

Since 11.1.0, SkyWalking stores and serves the conversations of long-lived AI agents. The feature requires the
[SkyWalking AI Sessionizer](https://github.com/apache/skywalking-ai-sessionizer) as the sender: it is the
producer of the files described here, and a record under this layer without their attributes is rejected. The
Sessionizer collects an agent
runtime's transcripts into two file formats, Session Data (`.sd`, the records as collected) and Session Flow
(`.sf`, an append-only chain of rounds that describe the conversation's structure), and pushes every file as one
OTLP log record. The OAP verifies each file on arrival, stores it verbatim, and answers a conversation query with
one `asz.view` document that a viewer renders without opening any file.

In the Sessionizer's model a **conversation** is the unit of storage, analysis and export. A **session** is the
source-runtime context a record came from, carried as provenance: one conversation may contain several sessions,
and a session belongs to exactly one conversation.

## How a file reaches the OAP

The sender puts these resource attributes on every request:

| Attribute             | Value                                                                          |
|-----------------------|--------------------------------------------------------------------------------|
| `service.name`        | the name the sender is configured with, or else the runtime that produced the session, such as `Claude Code` |
| `service.instance.id` | who is pushing, in words the people reading the OAP recognise: a mailbox, a name or a machine, `user@host` of the pushing machine by default |
| `service.layer`       | `AI_AGENT`                                                                     |

Each log record is one file. The body is the file's text. The record attributes name the file (`asz.format`,
`asz.file`, `asz.file.digest`, `asz.lines`, `asz.session`, `asz.seq` for a Session Data file; `asz.conversation`,
`asz.round`, the conversation's time range and its title and counts for a round). The two file formats are
documented by the Sessionizer under
[Session Data](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/session-data/) and
[Session Flow](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/session-flow/), and
the wire attributes under
[Export over OpenTelemetry](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/setup/export-otlp/).

The OAP routes these records like every other OTLP log: by layer, to the bundled LAL rule
`lal/ai-agent.yaml`. The rule's output type, `ConversationFile`, checks the body's sha256 against
`asz.file.digest` and its line count against `asz.lines`, and stores the file in the table its format names. A
file that fails either check is dropped and counted in the `ai_agent_conversation_files_rejected` self-observability
metric with the reason as a label, and so is a file larger than `maxFileBytes`, under the reason `size`: one
file over the storage's message limit fails the write it travels in, and every record behind it in that write
with it, so the limit is applied where one file is one record; a stored file is a verified file. A round's title and counts are read only
when the record carries them: they came with a later round header, and a round from before them lands and lists
with zero counts. The service and its instance appear on the service list under the `AI_AGENT` layer as for any
other log sender.

Nothing is folded or decoded at ingest, so an OAP cluster needs no shared state for this feature.

## Storage

Two record models, both super datasets:

| Model                   | One row per        | Keys                                                           | Stored only                                                                                                        |
|-------------------------|--------------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `ai_agent_session_data` | Session Data file  | `service_id`, `service_instance_id`, `session`, indexed `seq`  | `digest`, `body`                                                                                                   |
| `ai_agent_session_flow` | Session Flow round | `service_id`, `service_instance_id`, indexed `conversation`, `round` | `session_from_time`, `title`, `talks`, `steps`, `streams`, `segments`, `unresolved`, `changes`, `lines_added`, `lines_removed`, `llm_calls`, `subagents`, `bash_runs`, `digest`, `body`     |

A Session Data row carries nothing but its keys and the file: the file's kind, stream or run, time range and name
are on its first line and are read from there. A round's stored-only columns exist for the list page, which reads
them without opening a body; its `round` number is queryable so a long chain is read window by window. The row's
timestamp is the file's latest record time, or the conversation's last activity for a round, so a conversation's
files are found by its own time range. A row belongs to its sender: its id is the service, the instance and the
file's digest, so the same file pushed again by the same sender lands on the same row, and pushed by another
service or sender makes another.

- **BanyanDB**: both models live in their own group, `recordsAIAgent`, configured like the log group with hot,
  warm and cold stages under `SW_STORAGE_BANYANDB_AI_AGENT_*`, 30 days hot by default. Both tables expire
  together, because a round whose files are gone is a broken chain. See the
  [BanyanDB storage document](storages/banyandb.md).
- **Elasticsearch**: two super-dataset index families, `sw_ai_agent_session_data-*` and
  `sw_ai_agent_session_flow-*`, sharded by `superDatasetIndexShardsFactor`; retention is the single
  `recordDataTTL`. The columns the reads sort and range on, `seq`, `round` and `timestamp`, keep doc values.
- **JDBC** (MySQL, PostgreSQL, H2): two tables of their own; the body is stored as Base64 text, `LONGTEXT` on
  MySQL, since a body near `maxFileBytes` outgrows `MEDIUMTEXT` once encoded, `MEDIUMTEXT` on H2, a CLOB there,
  and `TEXT` on PostgreSQL; retention is `recordDataTTL`.

## Query

The list and the export are GraphQL queries in `ai-agent-conversation.graphqls`; the conversation itself is an
HTTP route on the same server, because its document is as large as the conversation.

- `listConversations(condition, duration)` lists one row per conversation of a service, optionally of one sender, from
  the newest round's attributes: its title, talks, steps, streams, segments and unresolved references, and the counts
  the Sessionizer writes on a round's header, `changes` with `linesAdded` and `linesRemoved`, `llmCalls`, `subagents`
  and `bashRuns`, each absent rather than zero when the round did not carry it. The rounds are read newest first, at
  most `limit` (default 1000), then folded to one row per conversation. An optional `conversation` narrows the read to
  one conversation by id, and an optional `title` keeps only the rows whose title contains the text,
  case-insensitively — matched after folding, on the newest round's title, so it never widens the rounds read.
- `getConversationRawFiles(condition, files)` lists every landed file and round of a conversation with its id,
  digest and size; selecting `body` returns the files verbatim, which is the export path. The optional `files`
  argument narrows the read to named files.

### The conversation view route

```
GET /ai-agent/conversations/{conversation}/v1/view?service={serviceName}[&instance={instanceName}]
```

It answers with the whole conversation, once, as one `asz.view` version 1.0 document, the document the
Sessionizer defines under
[The asz.view document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/)
and serves from its own viewer; the OAP's document equals it, key for key, for the same files. `v1` in the path
is the document version. The OAP reads the conversation's rounds over the whole retention window, then the
files of each session the head round names over the time range the head round carries, checks the chain, folds
the rounds, resolves every reference into the landed records, and renders the document. Verification is
content, not an error: a missing round or file, or a failed digest, is written into the document's
`summary.state` and `summary.problems`, and the rest of the document holds whatever could still be folded. The
fold shows as much as landed: a round that is missing, that does not read, or that the fold refuses is skipped,
the chain resumes at the next stored round, and the absent rounds are named once as a range, as are the files a
round names that did not land. The round the chain resumes at is listed unverified, because nothing links it to
what is absent; the rounds after it verify against it; `head` names the last round folded. This goes further
than the Sessionizer's own viewer, whose fold stops before the first gap. The document is built on every call
and nothing is cached.

| Parameter or header | Meaning |
|---|---|
| `service` / `serviceId` | the service by name, or by id; one of them is required |
| `instance` | optional, the sender's instance name from the list row; with it, every storage read is a full series lookup |
| `Accept` | `application/vnd.skywalking.asz.view+yaml`, or any type naming `yaml`, for YAML; anything else, JSON, as `asz conversation -json` prints it |
| `Content-Type` | names the document and its version, the HTTP way: `application/vnd.skywalking.asz.view+json; version=1.0` or `application/vnd.skywalking.asz.view+yaml; version=1.0`. The document's own first two keys, `format` and `version`, say the same |
| `Accept-Encoding` | the body is compressed when the client allows; a document is repetitive text and shrinks several times over |
| status | 200 with the document; 400 when no service is named; 404 when the service stores no round of the conversation; 500 on a storage failure. An error is `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)): `{"type": "about:blank", "title": "Not Found", "status": 404, "detail": "..."}` |

The route is on the core HTTP server beside `/graphql`, so it has the same host, port, context path and TLS
settings, and serves HTTP/1.1 and HTTP/2 alike. The body is streamed: it is written to the response as it is
rendered, never held whole in memory, and a slow client holds back the render. The route runs under its own
timeout, `viewRequestTimeout`, in place of the server's default of ten seconds, because the floor for a large
conversation is seconds of storage reads plus seconds of fold and render.

The conversation page of the UI makes one call, this route, and nothing else.

## Workspace changes

The Sessionizer's Claude Code plugin records, beside each tool call, which files the call changed and how, as a
git-style diff. Those records reach the OAP two ways, and the document shows both:

- **A `changes` file**, a Session Data file of kind `changes` under the stream the tool ran on,
  `<session>/streams/<stream>/changes-<stamp>-<seq>.sd`, one line per observed call. It lands, is verified and is
  stored like any other Session Data file: it takes a seq of its own between the transcript files that landed around
  it, a round's window covers it and its input digest chains it, and it is listed under `files` with its kind. Nothing
  about it is decoded at ingest.
- **The runtime's own patch.** Claude Code records a patch for its own `Edit`, `Write` and `NotebookEdit` calls, and
  the Sessionizer lands it as a second `data` part on the call's result record in the transcript, beside the raw
  result, which stays byte for byte.

Each record is a `changes/1` document: the tool-use id it belongs to, who captured it, `claude-code` for a patch the
runtime recorded or `asz-plugin` for one the plugin observed, the basis of the observation, the windows scanned, and
one entry per file with its operation, the hashes on both sides and the hunks. The view joins each record to its step
by the tool-use id, which the record names and the step's call part carries; nothing is matched by time. In the
`asz.view` document:

- `workspace_changes` lists every record of the session in time order, each with the `step` it belongs to and the
  `ref` it was read from, then the record's own fields as `changes/1` lists them;
- `summary.changes` counts them;
- a tool step lists the ids of its records under `changes`.

The runtime's record and the plugin's record of one call share its id and are both kept, the runtime's first. A record
with `basis: skipped_read_only` carries no changes and means the call was not observed, never that nothing changed. A
session folds to the same nodes with and without its `changes` files: they are evidence beside a stream, not steps of
it. The record and the entry are defined by the Sessionizer under
[The asz.view document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/), and
the plugin under
[The Claude Code plugin](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/setup/claude-code-plugin/).

## Configuration

```yaml
ai-agent-conversation:
  selector: ${SW_AI_AGENT_CONVERSATION:default}
  none:
  default:
    fileReadWindow: ${SW_AI_AGENT_CONVERSATION_FILE_READ_WINDOW:16}
    roundReadWindow: ${SW_AI_AGENT_CONVERSATION_ROUND_READ_WINDOW:16}
    maxListLimit: ${SW_AI_AGENT_CONVERSATION_MAX_LIST_LIMIT:10000}
    viewRequestTimeout: ${SW_AI_AGENT_CONVERSATION_VIEW_REQUEST_TIMEOUT:120}
    maxFileBytes: ${SW_AI_AGENT_CONVERSATION_MAX_FILE_BYTES:15728640}
    maxResponseBytes: ${SW_AI_AGENT_CONVERSATION_MAX_RESPONSE_BYTES:104857600}
```

| Key              | Meaning                                                                                                                                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `fileReadWindow` | how many Session Data files one storage query fetches, a batch size and not a limit: the view and the raw-file export read every file of the conversation, this many per query. Files are cut at 2 MiB, so a window is a few tens of megabytes; the window times `maxFileBytes` is the most one query can answer with. |
| `roundReadWindow` | how many Session Flow rounds one storage query fetches, the same way: the head round is fixed first, then the chain is read from round 1 to the head, this many per query. A round is cut at 2 MiB by the Sessionizer, and the same bound applies. |
| `maxListLimit`   | the most rounds one list query reads before folding, and the ceiling of the query's `limit` argument.                                       |
| `viewRequestTimeout` | how long one conversation view request may take, in seconds. |
| `maxFileBytes`   | the largest file stored, in bytes; a larger one is rejected at ingest and counted under the reason `size`. 15 MiB by default, under BanyanDB's 16 MiB gRPC message limit. The Sessionizer cuts files and rounds at 2 MiB; only a round from before that cut is larger. |
| `maxResponseBytes` | the most bytes one window read may answer with, applied to that read alone on a storage that caps a response per call. The BanyanDB client holds every other read to 50 MB; this module's two window reads carry it as a call option on the same connection, so nothing else changes. 100 MiB by default, above sixteen files at the 2 MiB cut with room for files landed whole. For a root of larger files, raise it or lower the windows, so that the window times `maxFileBytes` stays under it; a read over the limit fails as a storage error. |

### Turning the feature off

The GraphQL query module requires this module, so the `-` selector cannot remove it; `SW_AI_AGENT_CONVERSATION=none`
selects the `none` provider instead, which answers `listConversations` and `getConversationRawFiles` with an empty
result and an `errorReason` saying the module is disabled, and registers no conversation view route, so a `GET` on it
is a 404.

It also disables the two record models, so nothing of the feature reaches the storage: neither table is created, nor
the BanyanDB `recordsAIAgent` group, whose only members they are. A file the bundled LAL rule still verifies is
dropped for want of a record worker; drop `ai-agent` from `SW_LOG_LAL_FILES` as well to skip that work.

## Limits on the path

- The OAP's OTLP/HTTP endpoint accepts requests of up to 10 MiB, the HTTP server's default. The Sessionizer's
  request budget defaults to 8 MiB for that reason; a single file is cut at 2 MiB, so it always fits.
- The files of a conversation are read in windows of `fileReadWindow` files, and its rounds in windows of
  `roundReadWindow` rounds, per storage query, inside one view request. On BanyanDB each of those queries may answer
  with up to `maxResponseBytes`, 100 MiB by default, as a call option on the shared client in place of its 50 MB
  default, which every other read keeps; the window times `maxFileBytes` must stay under it. Elasticsearch answers
  at most 10,000 hits to one search.
- A read that is not bound to a duration, the view and the export, covers every retained stage: on BanyanDB the
  default stages and, when the group keeps one, the cold stage. A conversation the list found in cold storage
  is served, and one that spans stages is served whole.
- When the caller names no sender, the view and the export read across every sender of the service and keep one
  copy of a file or round two senders both pushed, so a Sessionizer renamed between pushes still yields the
  whole conversation.
- The `asz.view` document grows with the conversation. A session of 136 MB of landed files renders to a 70 MB
  document in about five seconds after about six seconds of storage reads, which is why the view is a streamed
  route with its own timeout and not a GraphQL query.

## Metrics of the agent runtime

Beside the files, the layer takes the agent runtime's own OpenTelemetry metrics, from either of two senders:

- **The Sessionizer**, with `metrics: true` on its `claude-code-local` adapter: `asz collect` derives Claude Code's
  token metric from the landed transcripts and sends it beside the files over the same connection, as `asz push` does
  for a storage root that is already there. It is a reconstructed subset of what the runtime's exporter sends: the
  same name, unit and kind, the four token types, `main` and `subagent` as the query source, the model and the
  session. Cost, active time and the user, terminal and attribution labels are not in a transcript, and are never
  estimated.
- **Claude Code's own exporter**, pointed at the OAP directly, `OTEL_EXPORTER_OTLP_ENDPOINT` on 11800 over gRPC or on
  12800 over HTTP, or through the Sessionizer's `claude-code-otlp` adapter, which lands each request as received: the
  full family with every label, and the exporter's other metrics with it.

Whichever sends, the resource must carry `service.layer=AI_AGENT`, which is what the rules filter on, and a
`service.instance.id` naming the sender; the Sessionizer sets both, `AI_AGENT` and `user@host` by default, and the
exporter takes them from `OTEL_RESOURCE_ATTRIBUTES`. The rule set is `otel-rules/ai-agent/*`, enabled by default in
`enabledOtelMetricsRules`: `runtime-service.yaml` gives each metric per service, one service per kind of runtime,
`Claude Code` by default, and `runtime-instance.yaml` the same per sender, under the prefixes `meter_ai_agent_` and
`meter_ai_agent_instance_`.

| Metric                     | Labels                                                                            | Value                                                                                   | From         |
|----------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|--------------|
| `tokens`                   |                                                                                   | tokens per minute, every type                                                           | both         |
| `tokens_by_type`           | `type`: `input`, `output`, `cacheRead`, `cacheCreation`                           | tokens per minute                                                                       | both         |
| `tokens_by_model`          | `model`, `type`                                                                   | tokens per minute                                                                       | both         |
| `tokens_by_source`         | `query_source`: `main`, `subagent`, and from the exporter also `auxiliary`; `type` | tokens per minute                                                                       | both         |
| `cache_read_share`         |                                                                                   | percent of what the model read that came from cache: `cacheRead` over every type but `output` | both         |
| `cost_by_model`            | `model`                                                                           | micro-dollars (USD × 1,000,000) per minute                                              | the exporter |
| `active_time`              | `type`: `user`, `cli`                                                             | milliseconds per minute                                                                 | the exporter |
| `sessions`                 |                                                                                   | sessions started per minute                                                             | the exporter |
| `lines_of_code`            | `type`: `added`, `removed`                                                        | lines per minute                                                                        | the exporter |
| `commits`, `pull_requests` |                                                                                   | per minute                                                                              | the exporter |
| `edit_decisions`           | `decision`: `accept`, `reject`                                                    | permission decisions on the editing tools per minute                                    | the exporter |

Three things to know when reading them:

- Every point is a delta, the tokens of the minute a call ended and not of the minute it ran, so a long call's tokens
  land in one minute. The receiver keeps a delta point as its value at its time, and a request that carries a series
  of minutes, as the Sessionizer's does, is analysed a minute at a time, see the
  [OpenTelemetry receiver](opentelemetry-receiver.md). The rules sum a minute's points over every session and sender
  and downsample by `SUM`, so an hour is the total over its minutes. `session.id` is summed away on purpose: a series
  per session is the cardinality of a busy team, and the conversation page knows a session's tokens from its own
  records.
- A metric value is a whole number, so a fraction is scaled first: cost to micro-dollars, active time to milliseconds,
  the cache share to percent.
- Cache reads dominate. On one five-day conversation they were 98% of all tokens, so a chart that stacks the four
  types shows a flat line for the other three unless it is split.

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
metric with the reason as a label; a stored file is a verified file. The service and its instance appear on the
service list under the `AI_AGENT` layer as for any other log sender.

Nothing is folded or decoded at ingest, so an OAP cluster needs no shared state for this feature.

## Storage

Two record models, both super datasets:

| Model                   | One row per        | Keys                                                           | Stored only                                                                                                        |
|-------------------------|--------------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `ai_agent_session_data` | Session Data file  | `service_id`, `service_instance_id`, `session`, indexed `seq`  | `digest`, `body`                                                                                                   |
| `ai_agent_session_flow` | Session Flow round | `service_id`, `service_instance_id`, indexed `conversation`, `round` | `session_from_time`, `title`, `talks`, `steps`, `streams`, `segments`, `unresolved`, `digest`, `body`     |

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
- **JDBC** (MySQL, PostgreSQL, H2): two tables of their own; the body is `MEDIUMTEXT` on MySQL and H2 and `TEXT`
  on PostgreSQL; retention is `recordDataTTL`.

## Query

The list and the export are GraphQL queries in `ai-agent-conversation.graphqls`; the conversation itself is an
HTTP route on the same server, because its document is as large as the conversation.

- `listConversations(condition, duration)` lists one row per conversation of a service, optionally of one sender,
  from the newest round's attributes. The rounds are read newest first, at most `limit` (default 1000), then
  folded to one row per conversation.
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
fold stops before a round that is missing, that does not read, or that belongs to another conversation,
session, parser or policy; `head` names the last round it reached, and the rounds after it are listed and not
verified, exactly as the Sessionizer's own viewer does. The document is built on every call and nothing is cached.

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

## Configuration

```yaml
ai-agent-conversation:
  selector: ${SW_AI_AGENT_CONVERSATION:default}
  default:
    fileReadWindow: ${SW_AI_AGENT_CONVERSATION_FILE_READ_WINDOW:16}
    roundReadWindow: ${SW_AI_AGENT_CONVERSATION_ROUND_READ_WINDOW:16}
    maxListLimit: ${SW_AI_AGENT_CONVERSATION_MAX_LIST_LIMIT:10000}
    viewRequestTimeout: ${SW_AI_AGENT_CONVERSATION_VIEW_REQUEST_TIMEOUT:120}
```

| Key              | Meaning                                                                                                                                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `fileReadWindow` | how many Session Data files one storage query fetches, a batch size and not a limit: the view and the raw-file export read every file of the conversation, this many per query. Files are cut at 2 MiB, and the BanyanDB client caps one response at 50 MB. |
| `roundReadWindow` | how many Session Flow rounds one storage query fetches, the same way: the head round is fixed first, then the chain is read from round 1 to the head, this many per query. A round is cut at 2 MiB by the Sessionizer. |
| `maxListLimit`   | the most rounds one list query reads before folding, and the ceiling of the query's `limit` argument.                                       |
| `viewRequestTimeout` | how long one conversation view request may take, in seconds. |

The GraphQL query module requires this module, so it cannot be disabled while the GraphQL query module is active.

## Limits on the path

- The OAP's OTLP/HTTP endpoint accepts requests of up to 10 MiB, the HTTP server's default. The Sessionizer's
  request budget defaults to 8 MiB for that reason; a single file is cut at 2 MiB, so it always fits.
- The BanyanDB client caps one query response at 50 MB, and Elasticsearch answers at most 10,000 hits to one search.
  The files of a conversation are read in windows of `fileReadWindow` files, and its rounds in windows of
  `roundReadWindow` rounds, per storage query, inside one view request.
- A read that is not bound to a duration, the view and the export, covers every retained stage: on BanyanDB the
  default stages and, when the group keeps one, the cold stage. A conversation the list found in cold storage
  is served, and one that spans stages is served whole.
- When the caller names no sender, the view and the export read across every sender of the service and keep one
  copy of a file or round two senders both pushed, so a Sessionizer renamed between pushes still yields the
  whole conversation.
- The `asz.view` document grows with the conversation. A session of 136 MB of landed files renders to a 70 MB
  document in about five seconds after about six seconds of storage reads, which is why the view is a streamed
  route with its own timeout and not a GraphQL query.

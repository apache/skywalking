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

The OAP routes these records like every other OTLP log: by layer, to the bundled LAL rule `lal/ai-agent.yaml`. The
rule's output type, `ConversationFile`, checks the body's sha256 against `asz.file.digest` and its line count against
`asz.lines`, and stores the file in the table its format names. A file that fails either check is dropped and counted
in the `ai_agent_conversation_files_rejected` self-observability metric with the reason as a label, and so is a file
larger than `maxFileBytes`, under the reason `size`: one file over the storage's message limit fails the write it
travels in, and every record behind it in that write with it, so the limit is applied where one file is one record; a
stored file is a verified file. A round's title and counts are read only when the record carries them: they came with
a later round header, and a round from before them lands and lists with zero talks, steps, streams, segments and
unresolved references, and no value for the counts added later: changes, lines added and removed, model calls,
subagents and Bash runs. The service and its instance appear on the service list under the `AI_AGENT` layer as for any
other log sender.

Nothing is folded or decoded at ingest, so an OAP cluster needs no shared state for this feature.

## Storage

Two record models, both super datasets:

| Model                   | One row per        | Keys                                                           | Stored only                                                                                                        |
|-------------------------|--------------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `ai_agent_session_data` | Session Data file  | `service_id`, `service_instance_id`, `session`, indexed `seq`  | `digest`, `body`                                                                                                   |
| `ai_agent_session_flow` | Session Flow round | `service_id`, `service_instance_id`, indexed `conversation`, `round` | `session_from_time`, `title`, `talks`, `steps`, `streams`, `segments`, `unresolved`, `changes`, `lines_added`, `lines_removed`, `llm_calls`, `subagents`, `bash_runs`, `digest`, `body`     |

A Session Data row carries nothing but its keys and the file: the file's kind, its stream or run, its collected time
and its seq are on its first line, its name is made from them, and its time range is read from its records. A round's
stored-only columns exist for the list page, which reads them without opening a body; its `round` number is queryable
so a long chain is read window by window. The row's timestamp is the file's latest record time, or the conversation's
last activity for a round, so a conversation's files are found by its own time range. A file whose records carry no
time, such as a manifest, a script or a child's meta file, takes the session's latest record time as known when it was
sent. When no record of the session has a time yet, it takes its own collected time. A round from before the last
activity was carried takes the time it was sent. A row belongs to its sender: its id is the service, the instance and
the file's digest, so the same file pushed again by the same sender lands on the same row, and pushed by another
service or sender makes another.

- **BanyanDB**: both models live in their own group, `recordsAIAgent`, configured like the log group with hot, warm
  and cold stages under `SW_STORAGE_BANYANDB_AI_AGENT_*`, 30 days hot by default. Both models follow the one
  retention, each row by its own time, so the oldest files of a long conversation can expire before the newest round
  that read them; the document then names them as missing. See the [BanyanDB storage document](storages/banyandb.md).
- **Elasticsearch**: two super-dataset index families, `sw_ai_agent_session_data-*` and
  `sw_ai_agent_session_flow-*`, sharded by `superDatasetIndexShardsFactor`; retention is the single
  `recordDataTTL`. The columns the reads sort and range on, `seq`, `round` and `timestamp`, keep doc values.
- **JDBC** (MySQL, PostgreSQL, H2): two tables of their own; the body is stored as Base64 text, `LONGTEXT` on
  MySQL, since a body near `maxFileBytes` outgrows `MEDIUMTEXT` once encoded, `MEDIUMTEXT` on H2, a CLOB there,
  and `TEXT` on PostgreSQL; retention is `recordDataTTL`.

## Session Data files and how they connect

A conversation is two kinds of file. Session Data files are the records as the Sessionizer collected them. Session
Flow rounds are the structure it assembled from them. Every Session Data file of a session has a `seq`, one counter
across every stream and every kind, so a session and a seq name exactly one file. A round names the range of files it
read, `from_seq` to `through_seq`, and its input digest chains the digest of every file in that range, so the OAP can
tell when a file is missing or changed. A node of a round points at the record it stands on by `seq`, `row` and
`block`: the file; the record in it, counted from 1 without the header, so row 1 is the file's second line; and, when
the node stands on one part of the record, the part, counted from 0. The document takes a step's text and time from
that record.

A file's kind is on its first line, the header, and so are its stream or run and its collected time `at`. The OAP
names the file from the header as the Sessionizer does: a prefix for its kind, the stamp from `at`, and the seq, such
as `transcript-20260101T000000.000000000Z-000001.sd`. These are the kinds:

| Kind                | Path under the session                    | Holds                                                           | Connects to the conversation                                                                                                             |
|---------------------|-------------------------------------------|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `transcript`        | `streams/<stream>/transcript-…`           | one execution stream: the main one, a child agent's, or an auxiliary one, the model calls made inside a tool | the round's nodes point at its records; almost every step takes its text and time from here                  |
| `agent_meta`        | `streams/<stream>/meta-…`                 | what the runtime recorded about a child agent when it began it  | the child's stream node takes its label from it, and a child of a child is tied to the agent that started it by it                      |
| `journal`           | `runs/<run>/journal-…`                    | a workflow run's journal                                        | ties the run's children to the call that launched them, and gives the value each child returned; a stream without a label is named from it |
| `workflow_manifest` | `runs/<run>/manifest-…`                   | a workflow run's manifest                                       | the call that launched the run, found by the run's batch, points at it and takes the run's name as `launched` in its attributes        |
| `workflow_script`   | `runs/<run>/script-…`                     | the program a workflow ran                                      | nothing; it is kept because it is part of the session                                                                                    |
| `changes`           | `streams/<stream>/changes-…`              | the files an observation saw change, one line per observation   | no node of a round; the document joins each record to its step by tool-use id, see [Workspace changes](#workspace-changes)               |
| `execution`         | `streams/<stream>/execution-…`            | what the plugin saw of each call to an MCP server, one per call | no node of a round; the document joins each record to its step by tool-use id, see [Tool executions](#tool-executions)                   |
| `provider_body`     | `provider_body/provider_body-…`           | the request and response bodies of model calls                  | the round carries the join: an `llm.call` names its bodies, and a reader rebuilds a body from the files, see [Provider bodies](#provider-bodies) |

Every kind is verified and stored the same way, nothing in it is decoded at ingest, and the document lists each file
under `files` with its kind. A kind the OAP does not know is stored and listed too, under its stream, or its run when
it names no stream. The kinds and their records are defined by the Sessionizer under
[Session Data](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/session-data/).

## Query

The list is a GraphQL query in `ai-agent-conversation.graphqls`. The conversation itself and its stored files are
HTTP routes on the same server, because a document is as large as the conversation, and the files larger still.

- `listConversations(condition, duration)` lists one row per conversation of a service, optionally of one sender, from
  the newest round's attributes: its title, talks, steps, streams, segments and unresolved references, and the counts
  the Sessionizer writes on a round's header, `changes` with `linesAdded` and `linesRemoved`, `llmCalls`, `subagents`
  and `bashRuns`, each absent rather than zero when the round did not carry it. The rounds are read newest first, at
  most `limit` (default 1000), then folded to one row per conversation. An optional `conversation` narrows the read to
  one conversation by id, and an optional `title` keeps only the rows whose title contains the text,
  case-insensitively — matched after folding, on the newest round's title, so it never widens the rounds read. On
  BanyanDB, `duration.coldStage: true` selects the cold stage; otherwise the query uses the default hot/warm stages.

### The conversation view route

```
GET /ai-agent/conversations/{conversation}/v1/view?service={serviceName}&instance={instanceName}[&coldStage=true]
```

It answers with the whole conversation, once, as one `asz.view` version 1.0 document, the document the Sessionizer
defines under [The asz.view
document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/) and serves from its
own viewer; the OAP's document equals it, key for key, for the same files. `v1` in the path is the document version. The
OAP reads the conversation's rounds over the whole retention window, then the files of each session the head round names
over the time range the head round carries, checks the chain, folds the rounds, resolves every reference into the landed
records, and renders the document. Verification is content, not an error: a missing round or file, or a failed digest,
is written into the document's `summary.state` and `summary.problems`, and the rest of the document holds whatever could
still be folded. The fold shows as much as landed: a round that is missing, that does not read, or that the fold refuses
is skipped, the chain resumes at the next stored round, and the absent rounds are named once as a range, as are the
files a round names that did not land. After a round that is missing or does not read, the round the chain resumes at is
listed unverified, because nothing links it to what is absent; a round the fold refused still reads, so the round after
it verifies against it. The rounds after the resumed one verify against it; `head` names the last round folded. This
goes further than the Sessionizer's own viewer, whose fold stops before the first gap. A round does not read when a line
of it is not a JSON object, or nests deeper than 256 levels, which no round the Sessionizer writes does. The document is
built on every call and nothing is cached.

| Parameter or header | Meaning |
|---|---|
| `service` | required, the service name |
| `instance` | required, the sender's instance name, as the list row names it, so every storage read is a full series lookup. Ingest stores an empty instance as `unknown`, so every row names one. A conversation whose sender was renamed partway has rounds and files under two instances; the route reads the named one, and the document names what it did not find under `summary.problems` |
| `coldStage` | optional, false by default. On BanyanDB, true selects only the cold stage; otherwise the read uses the default hot/warm stages. The UI passes its selected stage when opening a conversation. Other storages ignore it. |
| `Accept` | `application/vnd.skywalking.asz.view+yaml`, or any type naming `yaml`, for YAML; anything else, JSON, as `asz conversation -json` prints it |
| `Content-Type` | names the document and its version, the HTTP way: `application/vnd.skywalking.asz.view+json; version=1.0` or `application/vnd.skywalking.asz.view+yaml; version=1.0`. The document's own first two keys, `format` and `version`, say the same |
| `Accept-Encoding` | the body is compressed when the client allows; a document is repetitive text and shrinks several times over |
| status | 200 with the document; 400 when the service or the instance is not named, or when `coldStage` is neither true nor false; 404 when the sender stores no round of the conversation; 500 on a storage failure; 503 when `viewRequestTimeout` runs out before the document starts, and a response already started is cut short instead. An error is `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)): `{"type": "about:blank", "title": "Not Found", "status": 404, "detail": "..."}` |

The route is on the core HTTP server beside `/graphql`, so it has the same host, port, context path and TLS
settings, and serves HTTP/1.1 and HTTP/2 alike. The document is built whole from the folded rounds and the files it
reads, so its memory grows with the conversation; its text is then streamed, written to the response as it is
rendered, and a slow client holds back the render. The route runs under its own
timeout, `viewRequestTimeout`, in place of the server's default of ten seconds, because the floor for a large
conversation is seconds of storage reads plus seconds of fold and render.

The conversation page of the UI opens a conversation with this route. What a step only points at, such as the provider
bodies of an `llm.call`, it loads through the files route when a reader opens it.

### The conversation files route

``` GET
/ai-agent/conversations/{conversation}/v1/files?service={serviceName}&instance={instanceName}&session={session}&seq={seq}[&seq={seq}...][&coldStage=true]
```

It answers with chosen Session Data files of a conversation's session, streamed, so a page loads what a step points
at, such as the provider bodies of an `llm.call`, only when a reader opens it. A file is chosen by its session and its
landed seq: the Sessionizer assigns a seq once per file within a session, one counter for every stream and kind, and
the storage reads a file by exactly those two. The document's `files[]` gives every file's `seq` and its name, whose
first segment is its session. There is no read of every file: a reader chooses each one. The route reads the named
session under the named sender, so the session is the caller's to choose, within what that sender stores.

| Parameter or header | Meaning |
|---|---|
| `service`, `instance`, `coldStage` | as for the view route |
| `session` | required, the session the files belong to |
| `seq` | required, one to 32 times, a file's landed seq. The Sessionizer cuts a file at 2 MiB by default, so a response is usually no more than about 64 MiB; a file holding one larger record is larger, up to `maxFileBytes`. A reader wanting more files asks again |
| `Accept` | chooses the format. There is one, which any `Accept` gets: `application/vnd.skywalking.asz.files+ndjson` |
| `Accept-Encoding` | the body is compressed with gzip when the client allows. The route compresses it itself, a chunk at a time, so nothing compressed accumulates in memory |
| status | 200 with the files, none when no seq is stored; 400 when the service, the instance or the session is not named, when no seq is, when more than 32 are, when one is not a positive whole number, or when `coldStage` is neither true nor false; 404 when the sender stores no round of the conversation; 500 on a storage failure before the first file; 503 when the route's timeout runs out before the first file. A failure after the first file ends the response early. |

For each stored file, the body holds a naming line, then the file:

```
{"file":"<session>/provider_body/provider_body-<stamp>-000004.sd","seq":4,"lines":16,"bytes":27874,"digest":"..."}
{"h":1,"schema":"sd/1","seq":4,"kind":"provider_body",...}
...
{"t":"end","records":14,"digest":"..."}
```

The naming line carries the file's name as the document lists it, its `seq`, its own newline count `lines`, its size
`bytes`, and the sha256 of its bytes `digest`. It also carries `copies` where the read saw that seq more than once,
which happens when the same seq was stored with different bytes - two roots of one session pushed by one sender, after
a repack. The file served is the first, and `copies` says the others are there, so a reader can say so rather than
show one copy as the whole truth; the field is absent when there is one. It counts what the read returned rather than
what the storage holds, since a storage caps what one query answers with, so read it as "more than one". Exactly
`bytes` bytes follow: the file, byte for byte. A non-empty file that does not end with a newline is followed by one,
which is not part of it, so the next naming line starts a line; an empty file is followed by nothing. A file the
Sessionizer wrote ends with a newline, so a reader may equally take `lines` lines. Nothing in a file is escaped. The
files come in seq order, which is the order a reader must add provider bodies in, because a body refers to pieces and
bodies that landed before it. A seq no stored file answers is left out rather than failing the request. A line can be
as large as the largest file, so a reader must not assume short lines.

The files are read one storage window at a time and each window is written before the next is read, so a response is
never held whole. The files are read over the time range of the conversation's newest intact round among its newest
16, from its session's first activity to its last or the round's own stored time, whichever is later, even when the
view cannot fold that round; from the start of time up to the head round's own time when none of those 16 is intact. A
file stamped outside that range is left out.

## Workspace changes

The Sessionizer's Claude Code plugin records which files changed while a tool call ran, within the directories it
watches, and how, as a git-style diff. A record says how much it saw: a scan cut short has `coverage: partial` and may
list its `gaps`, and a file whose hunks could not be kept, such as one over the size cap, carries its hashes without
them. Those records reach the OAP two ways, and the document shows both:

- **A `changes` file**, a Session Data file of kind `changes` under the stream the tool ran on,
  `<session>/streams/<stream>/changes-<stamp>-<seq>.sd`, one line per observation: a call watched in several
  directories has one record for each, and a change no call's window covered has an unattributed one of its own, which
  names no tool. It lands, is verified and is stored like any other Session Data file: it takes a seq of its own
  between the transcript files that landed around it, a round's window covers it and its input digest chains it, and
  it is listed under `files` with its kind. Nothing about it is decoded at ingest.
- **The runtime's own patch.** Claude Code records what its own `Edit`, `Write` and `NotebookEdit` calls changed: a
  patch for the first two, and the notebook before and after for the third, whose hunks the Sessionizer works out. The
  Sessionizer lands the result as a second `data` part on the call's result record in the transcript, beside the raw
  result, which is kept as the runtime wrote it, apart from the white space between its JSON tokens.

Each record is a `changes/1` document: the tool-use id it belongs to, who captured it, `claude-code` for a patch the
runtime recorded or `asz-plugin` for one the plugin observed, the basis of the observation, the windows scanned, and
one entry per file with its operation, the hashes on both sides and the hunks. The view joins each record to its step
by the tool-use id, which the record names and the step's call part carries; nothing is matched by time. In the
`asz.view` document:

- `workspace_changes` lists, in time order, every record of the session's `changes` files and the runtime's own patch
  of every tool step the fold holds, each with the `step` it belongs to and the `ref` it was read from, then the
  record's own fields as `changes/1` lists them, in its order, a data part of another shape not being a record; a
  patch on a result record no step reads is not listed;
- `summary.changes` counts them;
- a tool step lists the ids of its records under `changes`.

A record is kept once by who captured it and its id. The runtime's record and the plugin's record of one call name the
same tool-use id and are both kept, and when their times are equal the runtime's comes first. A record that joins no
step, such as an unattributed one, is kept with an empty `step`. A record with `basis: skipped_read_only` carries no
changes and means the call was not observed, never that nothing changed. A session folds to the same nodes with and
without its `changes` files: they are evidence beside a stream, not steps of it. The record and the entry are defined
by the Sessionizer under [The asz.view
document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/), and the plugin
under [The Claude Code
plugin](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/setup/claude-code-plugin/).

## Tool executions

The Sessionizer's Claude Code plugin also records what each call to an MCP server did: which server ran it, where the
server's configuration came from, how the call ended and how long the runtime waited for it.

- **An `execution` file**, a Session Data file of kind `execution` under the stream the call ran on,
  `<session>/streams/<stream>/execution-<stamp>-<seq>.sd`, one line per observed call. It lands, is verified and is
  stored like any other Session Data file, and nothing about it is decoded at ingest.

Each record is an `execution/1` document: its own id, the tool-use id of the call it observed, who observed it and
where, the server, and the outcome, `returned`, `failed` or `interrupted`. When the hook reported them, it also has
the milliseconds the runtime measured around the call, the size and the SHA-256 of the arguments, and, for a call that
returned, the size and the SHA-256 of the answer; never their text. In the `asz.view` document:

- `tool_executions` lists every record of the session in time order, each with the `step` it belongs to and the
  `ref` it was read from, then the record's own fields as `execution/1` lists them, in its order, a data part of
  another shape not being a record; records of one instant are in the order they were read;
- a tool step lists the ids of its records under `executions`;
- a call to an MCP server carries `mcp_server` and `mcp_tool` in its `attrs` when its name, `mcp__<server>__<tool>`,
  splits into exactly one server and one tool. The attributes come with the round.

The join is the tool-use id, which the record names and the step's call part carries; nothing is matched by time. One
call can have several records, and a record is kept once by its own id, so a line landed twice is one record. A record
whose call is not a step of the document is kept with no step. A session folds to the same nodes with and without its
`execution` files. The record is defined by the Sessionizer under [The asz.view
document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/), and the plugin
under [The Claude Code
plugin](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/setup/claude-code-plugin/).

## Provider bodies

The Sessionizer can also land what each model call was sent and what came back. For Claude Code these are the request
and response bodies it exchanged with its model provider: a request carries what no transcript records, the system
prompt, the tool definitions and the reminders the runtime inserted. For LangChain and LangGraph they are each model
run's inputs and outputs, as the LangSmith client sent them. Every call sends its whole message list again, so the
Sessionizer cuts each body into what the session did not hold yet and a manifest that rebuilds it byte for byte, from
its own pieces and from pieces and bodies that landed before it. A body it cannot cut safely is kept whole, as an
`unknown` part that says why.

- **A `provider_body` file**, a Session Data file of kind `provider_body`, one directory for the session,
  `<session>/provider_body/provider_body-<stamp>-<seq>.sd`, one record per body. It lands, is verified and is stored
  like any other Session Data file, and a round's window covers it. Nothing about it is decoded at ingest, and a body
  is never rebuilt by the OAP.

In the `asz.view` document:

- an `llm.call` step lists its bodies under `provider_bodies`, its request and then its response, each as its `role`
  and the `ref` of the landed record, never the body itself;
- `summary.provider_bodies` counts the session's bodies as of the folded chain, joined or not, and
  `summary.captured_prompts` the calls whose request is listed.

The OAP does not make the join. The Sessionizer makes it when it parses a round, and the round carries it: an
`llm.call` node names its bodies in its `provider_bodies` attribute, and the `session` node states
`provider_bodies_landed`, how many bodies the session holds. The OAP reads both from the fold and opens no body to do
it. A round whose `provider_bodies` attribute is malformed, a value other than null that is not a list of bodies, a
role that is not `request` or `response`, a seq or row below one, or a reference past the round's own range, is refused
like a round with any other bad reference. Whether the record a body names exists and rebuilds is not checked here: a
missing file is the chain's problem, and a body is only rebuilt by a reader. The document lists the bodies on the
step, not in its `attrs`. A missing `provider_body` file does not change what a call names: the file is the chain's
problem, named under `summary.problems`.

A body refers to earlier records of the same session, sometimes in an earlier file, so a reader that wants a body
takes the `provider_body` entries of `files[]` with a seq up to the one its `ref` names, reads them through the files
route by session and seq, and rebuilds the body as the Sessionizer describes. The record, the manifest and the rules
of the join are defined by the Sessionizer under [Session
Data](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/session-data/), [Session
Flow](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/session-flow/) and [The asz.view
document](https://skywalking.apache.org/docs/skywalking-ai-sessionizer/next/en/formats/asz-view/).

## Configuration

```yaml
ai-agent-conversation:
  selector: ${SW_AI_AGENT_CONVERSATION:default}
  none:
  default:
    conversationListMaxLimit: ${SW_AI_AGENT_CONVERSATION_LIST_MAX_LIMIT:10000}
    viewRequestTimeout: ${SW_AI_AGENT_CONVERSATION_VIEW_REQUEST_TIMEOUT:120}
    readWindow: ${SW_AI_AGENT_CONVERSATION_READ_WINDOW:16}
    maxResponseBytes: ${SW_AI_AGENT_CONVERSATION_MAX_RESPONSE_BYTES:104857600}
    maxFileBytes: ${SW_AI_AGENT_CONVERSATION_MAX_FILE_BYTES:15728640}
```

| Key              | Meaning                                                                                                                                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `conversationListMaxLimit` | the most rounds one list query reads before folding, and the ceiling of the query's `limit` argument. It counts rounds, not conversations, so a busy conversation spends the budget of the quiet ones and a quiet one can fall off the list. |
| `viewRequestTimeout` | how long one conversation view request may take, in seconds. |
| `readWindow` | how many Session Data files, or Session Flow rounds, one storage query fetches. A batch size and not a limit: a view reads every round of the chain and every file of the conversation, and the files route the named ones, this many per query, so a conversation of 865 rounds is 55 queries at 16. Raising it trades bytes in one response for round trips, which are most of the wait before a view's first byte; it must stay within `maxResponseBytes`. Both are cut at 2 MiB by default by the Sessionizer, so a window is usually a few tens of megabytes. |
| `maxResponseBytes` | the most bytes one storage query may answer with. **BanyanDB alone accepts it**, carried as a call option on the shared client in place of the 50 MB it holds every other read to, so nothing else's read changes; Elasticsearch and JDBC ignore it and bound a read by hits and by rows. 100 MiB by default, above sixteen files at the 2 MiB cut with room for files landed whole. For a root whose files land whole, raise it or lower `readWindow`; a read over the limit fails as a storage error. |
| `maxFileBytes` | the largest file stored, in bytes; a larger one is rejected at ingest and counted under the reason `size`. 15 MiB by default, under BanyanDB's 16 MiB gRPC message limit. The Sessionizer cuts files at 2 MiB by default, and a file can still be larger: for a Claude Code transcript the budget is on the source bytes read, and when one source line is over it the file takes that line and every complete line after it; a provider body over it lands alone; a test lowers this to prove the rejection without pushing a file that size. |

### Turning the feature off

The GraphQL query module requires this module, so the `-` selector cannot remove it; `SW_AI_AGENT_CONVERSATION=none`
selects the `none` provider instead, which answers `listConversations` with an empty result and an `errorReason`
saying the module is disabled, and registers no conversation route, so a `GET` on the view or the files route is a
404.

It also disables the two record models, so nothing of the feature reaches the storage: neither table is created, nor
the BanyanDB `recordsAIAgent` group, whose only members they are. A file the bundled LAL rule still verifies is
dropped for want of a record worker; drop `ai-agent` from `SW_LOG_LAL_FILES` as well to skip that work.

## Limits on the path

- The OAP's OTLP/HTTP endpoint accepts requests of up to 10 MiB, the HTTP server's default. The Sessionizer's
  request budget defaults to 8 MiB for that reason, and a file is cut at 2 MiB by default. A file holding one record
  larger than that, and a round covering only such a file, are sent whole, so the receiver's limit and `maxFileBytes`
  must allow the largest one.
- The files of a conversation, and its rounds, are read in windows of `readWindow` per storage query, inside one
  view request. On BanyanDB each of those queries may answer
  with up to `maxResponseBytes`, 100 MiB by default, as a call option on the shared client in place of its 50 MB
  default, which every other read keeps. Elasticsearch answers
  at most 10,000 hits to one search.
- The view and the files route read over the retention window of the caller's selected stages. On BanyanDB, the
  default is hot/warm; cold is queried only when the caller explicitly sets `coldStage: true`. Every round and
  file read uses that same selection. A conversation spanning stages can therefore report missing rounds or
  files that are outside the selected stages.
- Both routes read one sender, the one the caller names, so every read is a full series lookup. A Sessionizer whose
  instance was renamed between pushes leaves a conversation's rounds and files under two instances; reading the
  newer instance, the document names what it did not find under `summary.problems`. A file or round the one sender
  pushed twice is kept once.
- The `asz.view` document grows with the conversation. A session of 136 MB of landed files renders to a 70 MB
  document in about five seconds after about six seconds of storage reads, which is why the view is a streamed
  route with its own timeout and not a GraphQL query.

## Metrics of the agent runtime

Beside the files, the Sessionizer's collection pipeline derives metrics from the files it lands and sends them over
the same connection; `asz push` sends the metrics a storage root already holds, and derives none. It derives them when
its `metrics.enabled` is on and sends them when its `export.otlp.metrics` is on, both by default. Its first derivation
over a root reaches back only `metrics.lookback`, 72 hours by default, so a conversation older than that has its files
in the OAP but not its metrics; `0` or `none` derives all of it. They are one family, named for any agent:

- `agent.token.usage`, the tokens of each finished model call of a Claude Code transcript: the four token types,
  `main` and `subagent` as the query source, the model and the session. For those calls it counts what Claude Code's
  own exporter calls `claude_code.token.usage`. The exporter also counts auxiliary calls, such as the one that names a
  session, which never reach a transcript, so their tokens are not here. Cost, active time and the user, terminal and
  attribution labels are not in a transcript either, and are never estimated.
- `agent.mcp.calls` and `agent.mcp.duration`, one per call to an MCP server the Claude Code plugin recorded, and the
  milliseconds the runtime measured around it: the server, the tool, the source of the server's configuration, the
  outcome and the query source. A call whose record has no duration counts as a call and adds no time; on Claude Code
  2.1.282 every record had one.

Claude Code's own exporter can also be pointed at the OAP directly, `OTEL_EXPORTER_OTLP_ENDPOINT` on 11800 over gRPC
or on 12800 over HTTP. The rules read its other metrics, cost, active time, sessions, lines of code, commits, pull
requests and edit decisions, and not its token metric: the tokens come from the Sessionizer, so a call is never
counted twice when both send. The exporter names its service `claude-code` unless told otherwise, and the rules group
by service, so set its `service.name` in `OTEL_RESOURCE_ATTRIBUTES` to the Sessionizer's service, `Claude Code` by
default, to see both on one service.

Whichever sends, the resource must carry `service.layer=AI_AGENT`, which is what the rules filter on, and a
`service.instance.id` naming the sender; the Sessionizer sets both, `AI_AGENT` and `user@host` by default, and the
exporter takes them from `OTEL_RESOURCE_ATTRIBUTES`. The rule set is `otel-rules/ai-agent/*`, enabled by default in
`enabledOtelMetricsRules`: `runtime-service.yaml` gives each metric per service, one service per kind of runtime,
`Claude Code` by default, and `runtime-instance.yaml` the same per sender, under the prefixes `meter_ai_agent_` and
`meter_ai_agent_instance_`.

| Metric                     | Labels                                                                            | Value                                                                                   | From                   |
|----------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|------------------------|
| `tokens`                   |                                                                                   | tokens per minute, every type                                                           | the Sessionizer        |
| `tokens_by_type`           | `type`: `input`, `output`, `cacheRead`, `cacheCreation`                           | tokens per minute                                                                       | the Sessionizer        |
| `tokens_by_model`          | `model`, `type`                                                                   | tokens per minute                                                                       | the Sessionizer        |
| `tokens_by_source`         | `query_source`: `main`, `subagent`; `type`                                        | tokens per minute                                                                       | the Sessionizer        |
| `cache_read_share`         |                                                                                   | percent of what the model read that came from cache: `cacheRead` over every type but `output` | the Sessionizer        |
| `cost_by_model`            | `model`                                                                           | micro-dollars (USD × 1,000,000) per minute                                              | Claude Code's exporter |
| `active_time`              | `type`: `user`, `cli`                                                             | milliseconds per minute                                                                 | Claude Code's exporter |
| `sessions`                 |                                                                                   | sessions started per minute                                                             | Claude Code's exporter |
| `lines_of_code`            | `type`: `added`, `removed`                                                        | lines per minute                                                                        | Claude Code's exporter |
| `commits`, `pull_requests` |                                                                                   | per minute                                                                              | Claude Code's exporter |
| `edit_decisions`           | `decision`: `accept`, `reject`                                                    | permission decisions on the editing tools per minute                                    | Claude Code's exporter |

`mcp_endpoint.yaml` gives the MCP metrics per endpoint, under the prefix `meter_ai_agent_mcp_`. An endpoint is one MCP
target the agent called, named `<server>/<tool>` under the agent's service, such as `status/lookup`. When the
runtime's name for a call does not split into exactly one server and one tool, the tool is that whole name.

| Metric             | Labels                                        | Value                                                                                      |
|--------------------|-----------------------------------------------|--------------------------------------------------------------------------------------------|
| `calls`            |                                               | calls per minute                                                                           |
| `calls_by_outcome` | `outcome`: `returned`, `failed`, `interrupted` | calls per minute                                                                           |
| `duration`         |                                               | milliseconds per minute, summed over the calls; divided by `calls`, the mean when every call had a duration, as every one did on Claude Code 2.1.282 |

When reading them:

- Every point is a delta, the tokens of the minute a call ended and not of the minute it ran, so a long call's tokens
  land in one minute; an MCP call counts in the minute it was observed. The Sessionizer stamps a point with the end of
  that minute, and the receiver keeps a delta point as its value at its time, so a call's tokens show under the next
  minute; a point the Sessionizer places after a later one of its series, to keep a series' windows apart, shows later
  still. A request that carries a series of minutes, as the Sessionizer's does, is analysed a minute at a time, see
  the [OpenTelemetry receiver](opentelemetry-receiver.md). The rules sum a minute's points over every session and
  sender and downsample by `SUM`, so an hour is the total over its minutes. `cache_read_share` is the exception: it is
  a percent computed for each request's points in a minute, and a minute or an hour holds the average of those
  percents, not the share of all the tokens in it. `session.id` is summed away on purpose: a series per session is the
  cardinality of a busy team, and the conversation page knows a session's tokens from its own records.
- A metric value is a whole number, so a fraction is scaled first: cost to micro-dollars, active time to milliseconds,
  the cache share to percent.
- An MCP call's duration is the runtime's own time around the call. It includes any wait before the call started, and
  it is not the server's own time.
- Cache reads dominate. On one five-day conversation they were 98% of all tokens, so a chart that stacks the four
  types shows a flat line for the other three unless it is split.

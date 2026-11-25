# Trace Pairing Debugging API

An OTLP call is turned into a network address alias by pairing its two spans, on the OAP node that owns the trace.
This API shows what that pairing does: the halves it receives, which of them match, and the alias each match builds.
It is not a TAL rule's run. A rule's run only asks for a half, with `prepareNetworkAlias` or `buildNetworkAlias`, and
the [TAL debugger](dsl-debugging-tal.md) shows that request and whether the half was sent. What happens to the half
after that is shown here.

## How the pairing works

1. Once every rule of an OTLP span has run, the span sends at most one caller half (from `prepareNetworkAlias`: its
   service and the peer it dialed) and one callee half (from `buildNetworkAlias`: its service and the rule that
   composes the address), for a trace that falls within `pairingSamplePercent`.
2. Each half goes to the OAP node that owns the trace, chosen by the trace id's hash; a single node sends to itself.
3. That node keeps the halves for 60 seconds: callers by their own span id, callees by their parent span id. A callee
   meets the caller whose span is its parent.
4. On a match, the callee rule's `buildNetworkAlias` expression composes the alias address from the caller half, and
   the alias, address to the callee's service, is pushed to the alias stream.
5. Every OAP node reloads the stored aliases into its alias cache every 10 seconds, and a later run reads them with
   `resolveNetworkAlias`.

## Enabling

The `trace-pairing-debugging` module hosts the routes on `admin-server` (default port `17128`). It is on by default,
and requires the `trace-analyzer` module:

```yaml
trace-pairing-debugging:
  selector: ${SW_TRACE_PAIRING_DEBUGGING:default}
  default:
```

Set `SW_TRACE_PAIRING_DEBUGGING=-` when `trace-analyzer` is removed.

## The capture

There are no sessions. Each OAP node has one capture, shared by every reader, and a request to any node acts on all
of them, as each node owns a share of the traces. The capture is off until someone starts it; while it is off, the
pairing pays one volatile read per half. Once started, it runs as long as it is read: every read of what it found is
a ping, and it stops a minute after the latest one, or when someone stops it; a read or a start that finds it idle
past that minute finds it stopped. A stop, or the expiry, releases what it found, as a DSL debug session's records go
when it stops, so OAP keeps nothing between two captures and a client keeps what it read.

Each capture from a start to its stop is a **round**, named by its `roundId`. A start gives every node the round of
the node that took it: a new one, under a new id and that node's start time, or the one that node already runs. A
node that is off starts that round; a node already capturing keeps its own and says which. So a node a start reaches
late joins the others' round, while a node restarted and started again apart, by a start the others missed, holds a
round of its own.

It keeps three lists on each node, each holding its 1000 most recently seen entries:

- **matched peers** — each peer a caller dialed, with the server it was matched to: the callee service of the latest
  match, and the server before it when a later match moved it to another;
- **unmatched peers** — each peer whose caller halves left the buffer without their pair;
- **unmatched servers** — each server whose callee halves did.

A half leaves the buffer unmatched when it `expired` after 60 seconds, was `evicted` from a full buffer, or was
`replaced` by a later half of the same span. A buffer drops expired halves when it is next written, so an expiry is
counted then.

## Routes

### Status

```text
GET /trace-pairing-debugging/status
```

The node that answered and its capture: `{"module": "trace-pairing-debugging", "nodeId": "…", "capture": {"state":
"off", …}}`. It does not read what the capture found, so it keeps nothing running; a client reads it to tell whether
this OAP serves the pairing debugging.

### Start and stop

```text
POST /trace-pairing-debugging/start
POST /trace-pairing-debugging/stop
```

Start turns the capture on on every node, as a round where it was off; stop turns it off and releases what it found.
Each answers with every node's capture: `state` (`off` or `capturing`), `roundId` (empty while it is off),
`startTime` and `lastReadTime` (both `0` while it is off), and `idleMillis`.

### Read what the capture found

```text
GET /trace-pairing-debugging/pairings?limit=1000
```

The counts and the three lists of one round, summed over the nodes in it, each list the most recently seen first and
at most `limit` long (1 to 1000, 1000 by default). A read is of the round most capturing nodes hold (as many in
two: the one started first), named by its `roundId`; a node in another round is listed in `nodes` with its own
`roundId` and not summed in. A read keeps the capture running.

```json
{
  "state": "capturing",
  "roundId": "6f1c2a4e-0b7d-4f53-9a8e-2d61c0e4b9a1",
  "startTime": 1795524158033,
  "lastReadTime": 1795524221877,
  "idleMillis": 60000,
  "received": {"caller": 42, "callee": 40},
  "matched": 38,
  "unmatched": {"caller": {"expired": 3}, "callee": {"expired": 1}},
  "matchedPeers": [
    {
      "peer": "backend:8080",
      "server": "backend",
      "callers": ["frontend"],
      "address": "frontend->backend:8080",
      "count": 38,
      "outcomes": {"registered": 38},
      "nodes": ["10.0.0.5:17129", "10.0.0.6:17129"],
      "firstTime": 1795524160012,
      "lastTime": 1795524221877
    }
  ],
  "unmatchedPeers": [
    {
      "peer": "cart:7070",
      "callers": ["checkout"],
      "count": 3,
      "causes": {"expired": 3},
      "firstTime": 1795524170000,
      "lastTime": 1795524200000,
      "traceIds": ["9e2f…", "41aa…", "07c3…"],
      "nodes": ["10.0.0.6:17129"]
    }
  ],
  "unmatchedServers": [
    {
      "server": "payment",
      "rules": ["otlp-general"],
      "count": 1,
      "causes": {"expired": 1},
      "firstTime": 1795524190000,
      "lastTime": 1795524190000,
      "traceIds": ["c0de…"],
      "nodes": ["10.0.0.5:17129"]
    }
  ],
  "nodes": [{"peer": "10.0.0.5:17129", "nodeId": "oap-1_11800", "status": "ok", "state": "capturing",
             "roundId": "6f1c2a4e-0b7d-4f53-9a8e-2d61c0e4b9a1", "matched": 20},
            {"peer": "10.0.0.6:17129", "nodeId": "oap-2_11800", "status": "ok", "state": "capturing",
             "roundId": "6f1c2a4e-0b7d-4f53-9a8e-2d61c0e4b9a1", "matched": 18}]
}
```

- `nodes` lists every OAP node the read asked, by its admin address as the cluster registry lists it (standalone
  included), with its state, its round and its calls matched; start and stop name the nodes alike. Each entry of the three lists names
  the `nodes` that hold it: the owners of the traces whose halves it counts, the node that saw it last first.
- `received` counts the halves the nodes received, by role; `matched`, the calls whose two halves met; `unmatched`,
  the halves that left without their pair, by role and cause.
- A matched peer has the `server` its latest match answered, and `previousServer` when a later match, or another
  node, moved it to another server: the server matched most recently before the latest one, with `previousTime`, its
  last match, and `updatedTime`, when the peer moved off it, when a node saw the move; the latest five `callers` that dialed it; the `address`
  its latest match composed, absent when that match composed none; its `count` of calls; and each match's `outcome`:

  | `outcome` | Meaning |
  |---|---|
  | `registered` | the alias was pushed to the alias stream |
  | `gateway` | the caller dialed a peer listed in `gateways.yml`, which keeps its own node; no alias |
  | `uncomposed` | the callee's rule composed no address: not loaded on this node, or its expression failed or returned nothing |
  | `tooLong` | the address does not fit the alias column |
  | `noService` | the callee's service name formats to an empty name |

- An unmatched peer or server has its `count`, its `causes`, the latest five `callers` or `rules`, and the latest
  five `traceIds`, to look the traces up.

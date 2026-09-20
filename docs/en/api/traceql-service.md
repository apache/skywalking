# TraceQL Service
TraceQL ([Trace Query Language](https://grafana.com/docs/tempo/latest/traceql/)) is Grafana Tempo's query language for traces.
TraceQL Service exposes Tempo Querying HTTP APIs including TraceQL expression system and OpenTelemetry Protocol (OTLP) trace format.
Third-party systems or visualization platforms that already support Tempo and TraceQL (such as Grafana), could obtain traces through TraceQL Service.

SkyWalking supports three types of traces: SkyWalking native traces, Zipkin-compatible traces, and natively stored OTLP traces. The TraceQL Service converts the first two formats to [Tempo's format](https://github.com/grafana/tempo/blob/main/pkg/tempopb/tempo.proto) 
to provide compatibility with Grafana Tempo and TraceQL queries. Since the trace detail part of Tempo's format (`Trace` message) reuses [OTLP Trace](https://opentelemetry.io/docs/reference/specification/protocol/) definitions, 
the conversion descriptions below refer to OTLP field names (e.g., span kind, status code). Natively stored OTLP traces need no conversion, see [OTLP Native Trace](#otlp-native-trace).

> **Note**: SkyWalking native trace support in TraceQL is based on the [Query Traces V2 API](https://skywalking.apache.org/docs/main/next/en/api/query-protocol/#trace-v2) (`queryTraces` / `hasQueryTracesV2Support`).
> Currently, only **BanyanDB** storage implements this API. Other storage backends (e.g. Elasticsearch, MySQL, PostgreSQL) do not support it.
> Zipkin-compatible traces are not subject to this restriction.

## Details Of Supported TraceQL
The following doc describes the details of the supported protocol and compared it to the TraceQL official documentation.
If not mentioned, it will not be supported by default.

### [TraceQL Queries](https://grafana.com/docs/tempo/latest/traceql/)
The expression supported by TraceQL is composed of the following parts (expression with [✅] is implemented in SkyWalking):
- [x] **Spanset Filter**: Basic span attribute filtering within braces `{}`
- [x] **Attribute Filtering**: Filter by span attributes (scoped and unscoped)
  - [x] `.service.name` - Service name (unscoped)
  - [x] `resource.service.name` - Service name (scoped)
  - [x] `span.<tags>` - Any span tags with scope (e.g., `span.http.method`, `span.http.status_code`, etc.)
- [x] **Intrinsic Fields**: Built-in trace fields, plain or with Tempo's `span:` prefix (`span:kind`)
  - [x] `duration` - Span duration with comparison operators (supports units: us/µs, ms, s, m, h. Default unit: microseconds. Minimum: microseconds, Maximum: hours)
  - [x] `name` - Span name
  - [x] `status` - Span status: `error`, `ok`, `unset`; the Zipkin datasource filters `error` only, the SkyWalking datasource `error` and `ok`
  - [x] `kind` - Span kind: `unspecified`, `internal`, `server`, `client`, `producer`, `consumer`; OTLP datasource only
- [x] **Comparison Operators**: 
  - [x] `=` - Equals
  - [x] `>` - Greater than (for duration)
  - [x] `>=` - Greater than or equal (for duration)
  - [x] `<` - Less than (for duration)
  - [x] `<=` - Less than or equal (for duration)
  - [ ] `!=` - Not equals
- [ ] **Negation** (`!`) and **attribute existence** (`{ span.foo }` without a comparison)
- [ ] **OR inside a spanset** (`{ a || b }`), which Grafana's query builder emits for a multi-select value
- [ ] **Spanset Logical Operations**: AND/OR between spansets (e.g., `{...} AND {...}`)
- [ ] **Pipeline Operations**: `|` operator for aggregations
- [ ] **Aggregate Functions**: count(), avg(), max(), min(), sum()
- [ ] **Regular Expression**: `=~` and `!~` operators

An expression that uses anything unsupported, a syntax error, an attribute written without a scope or a leading dot
(`http.method` instead of `.http.method` or `span.http.method`), or a value a datasource cannot filter (`kind` on the
Zipkin and SkyWalking datasources, `resource.instance` or `name` without a service on the SkyWalking datasource) is
refused with `400` and a message naming the construct. It is never answered with the predicate dropped, because a
full page of traces under a filter that excludes them reads as data.

Here are some typical TraceQL expressions used in SkyWalking:
```traceql
# Query traces by service name
{resource.service.name="frontend"}
```
```traceql
# Query traces by duration (greater than) - supports various time units
{duration>100ms}      # 100 milliseconds
{duration>1s}         # 1 second
{duration>100us}      # 100 microseconds (minimum unit)
{duration>1h}         # 1 hour (maximum unit)
```
```traceql
# Query traces by duration (less than)
{duration<1s}
```
```traceql
# Query traces by duration range
{duration>100ms && duration<10s}
```
```traceql
# Query traces with complex conditions
{resource.service.name="frontend" && span.http.method="GET" && duration>100ms}
```
```traceql
# Query traces by span name
{name="GET /api"}
```
```traceql
# Query traces by status
{status=ok}                 # error, ok or unset; the Zipkin datasource filters error only
```

**Duration Units**:
- `us` or `µs` - Microseconds (default unit, minimum precision)
- `ms` - Milliseconds
- `s` - Seconds
- `m` - Minutes
- `h` - Hours (maximum unit)

### Supported Scopes
TraceQL supports the following attribute scopes (scope with [✅] is implemented in SkyWalking):
- [x] `resource` - Resource attributes (e.g., `resource.service.name`)
- [x] `span` - Span tags (e.g., `span.http.method`, `span.http.status_code`, `span.db.statement`, etc.)
- [x] `intrinsic` - Built-in fields (e.g., `duration`, `name`, `status`)
- [ ] `event` - Span event attributes
- [ ] `link` - Span link attributes

## Details Of Supported HTTP Query API

### Build Info
Get build information about the Tempo instance.

```text
GET /api/status/buildinfo
```

**Parameters**: None

**Example**:
```text
GET /zipkin/api/status/buildinfo
```

**Response**:
```json
{
  "version": "v2.9.0",
  "revision": "",
  "branch": "",
  "buildUser": "",
  "buildDate": "",
  "goVersion": ""
}
```

### Search Tags (v1)
Get all discovered tag names within a time range.

```text
GET /api/search/tags
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| scope     | Scope to filter tags          | yes      |
| limit     | Maximum number of tags        | yes      |
| start     | Start timestamp (seconds)     | yes      |
| end       | End timestamp (seconds)       | yes      |

**Example**:
```text
GET /zipkin/api/search/tags?start=1640000000&end=1640100000
```

**Response**:
```json
{
  "tagNames": [
    "http.method",
    "http.status_code",
    "service.name"
  ]
}
```

### Search Tags (v2)
Get all discovered tag names with type information.

```text
GET /api/v2/search/tags
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| q         | TraceQL filter. With one, the `resource` and `span` names come from the spans of the newest 50 matching traces, so only keys those spans carry are offered | yes      |
| scope     | Scope to filter tags: `resource`, `span`, `intrinsic`; `event`, `link` and `instrumentation` are accepted and empty | yes      |
| limit     | Maximum number of tags per scope | yes      |
| start     | Start timestamp (seconds)     | yes      |
| end       | End timestamp (seconds)       | yes      |

**Example**:
```text
GET /zipkin/api/v2/search/tags?start=1640000000&end=1640100000
```

**Response**:
```json
{
  "scopes": [
    {
      "name": "resource",
      "tags": [
        "service"
      ]
    },
    {
      "name": "span",
      "tags": [
        "http.method"
      ]
    }
  ]
}
```

### Search Tag Values (v1)
Get all discovered values for a given tag.

```text
GET /api/search/tag/{tagName}/values
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| tagName   | Name of the tag               | no       |
| limit     | Maximum number of values      | yes      |
| start     | Start timestamp (seconds)     | yes      |
| end       | End timestamp (seconds)       | yes      |

**Example**:
```text
GET /zipkin/api/search/tag/resource.service.name/values?start=1640000000&end=1640100000
```

**Response**:
```json
{
  "tagValues": [
    "frontend",
    "backend"
  ]
}
```

### Search Tag Values (v2)
Get all discovered values for a given tag with optional filtering.

```text
GET /api/v2/search/tag/{tagName}/values
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| tagName   | `resource.<key>`, `span.<key>`, Tempo's unscoped `.<key>`, or an intrinsic such as `status` or `span:status` | no       |
| q         | TraceQL filter. A filter that names only a service is answered from the per-service catalogs (`name`, `resource.remote.service`, `resource.instance`); any narrower filter is answered from the spans of the newest 50 matching traces. `status` and `kind` always list their full enum | yes      |
| limit     | Maximum number of values      | yes      |
| start     | Start timestamp (seconds)     | yes      |
| end       | End timestamp (seconds)       | yes      |

**Example**:
```text
GET /zipkin/api/v2/search/tag/span.http.method/values?start=1640000000&end=1640100000
```

**Response**:
```json
{
  "tagValues": [
    {
      "type": "string",
      "value": "GET"
    },
    {
      "type": "string",
      "value": "POST"
    }
  ]
}
```

### Search Traces
Search for traces matching the given TraceQL criteria.

```text
GET /api/search
```

| Parameter   | Definition                                      | Optional       |
|-------------|-------------------------------------------------|----------------|
| q           | TraceQL query                                   | yes            |
| tags        | Tempo's deprecated logfmt form, `key=value` pairs separated by spaces, used when `q` is absent: `service.name` and `span.name` (or `name`) select the service and the span, every other key is an attribute equality. A pair that is not `key=value` is refused with 400 | yes            |
| minDuration | Minimum trace duration                          | yes            |
| maxDuration | Maximum trace duration                          | yes            |
| limit       | Maximum number of traces to return. Default: 20 | yes            |
| start       | Start timestamp (seconds)                       | yes            |
| end         | End timestamp (seconds)                         | yes            |
| spss        | Spans listed per span set. Default: 3           | yes            |

Every datasource lists only the spans that matched the query in each `spanSets` entry, re-evaluated in memory against
the same conditions the storage applied, and caps the list at `spss`; `matched` counts every matching span. Each trace
also carries `serviceStats`, the span and error counts per service over the whole trace.

**Example**:
```text
GET /zipkin/api/search?q={resource.service.name="frontend"}&start=1640000000&end=1640100000&limit=10
```

**Response**:
```json
{
  "traces": [
    {
      "traceID": "72f277edac0b77f5",
      "rootServiceName": "frontend",
      "rootTraceName": "post /",
      "startTimeUnixNano": "1772160307930523000",
      "durationMs": 3,
      "spanSets": [
        {
          "spans": [
            {
              "spanID": "6fa14d18315e51e5",
              "startTimeUnixNano": "1772160307932668000",
              "durationNanos": "875000",
              "attributes": [
                {
                  "key": "http.method",
                  "value": {
                    "stringValue": "GET"
                  }
                },
                {
                  "key": "http.path",
                  "value": {
                    "stringValue": "/api"
                  }
                },
                {
                  "key": "service.name",
                  "value": {
                    "stringValue": "backend"
                  }
                },
                {
                  "key": "span.kind",
                  "value": {
                    "stringValue": "SPAN_KIND_SERVER"
                  }
                }
              ]
            },
            {
              "spanID": "a52810585ca5a24e",
              "startTimeUnixNano": "1772160307930948000",
              "durationNanos": "2907000",
              "attributes": [
                {
                  "key": "http.method",
                  "value": {
                    "stringValue": "GET"
                  }
                },
                {
                  "key": "http.path",
                  "value": {
                    "stringValue": "/api"
                  }
                },
                {
                  "key": "service.name",
                  "value": {
                    "stringValue": "frontend"
                  }
                },
                {
                  "key": "span.kind",
                  "value": {
                    "stringValue": "SPAN_KIND_CLIENT"
                  }
                }
              ]
            },
            {
              "spanID": "72f277edac0b77f5",
              "startTimeUnixNano": "1772160307930523000",
              "durationNanos": "3531000",
              "attributes": [
                {
                  "key": "http.method",
                  "value": {
                    "stringValue": "POST"
                  }
                },
                {
                  "key": "http.path",
                  "value": {
                    "stringValue": "/"
                  }
                },
                {
                  "key": "service.name",
                  "value": {
                    "stringValue": "frontend"
                  }
                },
                {
                  "key": "span.kind",
                  "value": {
                    "stringValue": "SERVER"
                  }
                }
              ]
            }
          ],
          "matched": 3
        }
      ]
    }
  ]
}
```

### Query Trace by ID (v1)
Query a specific trace by its trace ID.

```text
GET /api/traces/{traceId}
```

| Parameter  | Definition                | Optional |
|------------|---------------------------|----------|
| traceId    | Trace ID                  | no       |
| start      | Start timestamp (seconds) | yes       |
| end        | End timestamp (seconds)   | yes       |
**Headers**:
- `Accept: application/json` - Return JSON format (default)
- `Accept: application/protobuf` - Return Protobuf format

**Example**:
```text
GET /zipkin/api/traces/abc123def456
```

**Response (JSON)**:YY
See Query Trace by ID (v2) below for response format.

### Query Trace by ID (v2)
Query a specific trace by its trace ID with OpenTelemetry format.

```text
GET /api/v2/traces/{traceId}
```

| Parameter  | Definition                | Optional |
|------------|---------------------------|----------|
| traceId    | Trace ID                  | no       |
| start      | Start timestamp (seconds) | yes      |
| end        | End timestamp (seconds)   | yes       |

**Headers**:
- `Accept: application/json` - Return JSON format (default)
- `Accept: application/protobuf` - Return Protobuf format

**Example**:
```text
GET /zipkin/api/v2/traces/f321ebb45ffee8b5
```

**Response (JSON - OpenTelemetry format)**:
```json
{
  "trace": {
    "resourceSpans": [
      {
        "resource": {
          "attributes": [
            {
              "key": "service.name",
              "value": {
                "stringValue": "backend"
              }
            }
          ]
        },
        "scopeSpans": [
          {
            "scope": {
              "name": "zipkin-tracer",
              "version": "1.0.0"
            },
            "spans": [
              {
                "traceId": "f321ebb45ffee8b5",
                "spanId": "2ddb7e272be2361d",
                "parentSpanId": "234138bd7d516add",
                "name": "get /api",
                "kind": "SPAN_KIND_SERVER",
                "startTimeUnixNano": "1772164123382182000",
                "endTimeUnixNano": "1772164123383730000",
                "attributes": [
                  {
                    "key": "http.method",
                    "value": {
                      "stringValue": "GET"
                    }
                  },
                  {
                    "key": "http.path",
                    "value": {
                      "stringValue": "/api"
                    }
                  },
                  {
                    "key": "net.host.ip",
                    "value": {
                      "stringValue": "172.23.0.4"
                    }
                  },
                  {
                    "key": "net.peer.ip",
                    "value": {
                      "stringValue": "172.23.0.5"
                    }
                  },
                  {
                    "key": "net.peer.port",
                    "value": {
                      "stringValue": "53446"
                    }
                  }
                ],
                "events": [
                  {
                    "timeUnixNano": "1772164123382256000",
                    "name": "wr",
                    "attributes": []
                  },
                  {
                    "timeUnixNano": "1772164123383409000",
                    "name": "ws",
                    "attributes": []
                  }
                ],
                "status": {
                  "code": "STATUS_CODE_UNSET"
                }
              }
            ]
          }
        ]
      },
      {
        "resource": {
          "attributes": [
            {
              "key": "service.name",
              "value": {
                "stringValue": "frontend"
              }
            }
          ]
        },
        "scopeSpans": [
          {
            "scope": {
              "name": "zipkin-tracer",
              "version": "0.1.0"
            },
            "spans": [
              {
                "traceId": "f321ebb45ffee8b5",
                "spanId": "234138bd7d516add",
                "parentSpanId": "f321ebb45ffee8b5",
                "name": "get",
                "kind": "SPAN_KIND_CLIENT",
                "startTimeUnixNano": "1772164123379290000",
                "endTimeUnixNano": "1772164123384163000",
                "attributes": [
                  {
                    "key": "http.method",
                    "value": {
                      "stringValue": "GET"
                    }
                  },
                  {
                    "key": "http.path",
                    "value": {
                      "stringValue": "/api"
                    }
                  },
                  {
                    "key": "net.host.ip",
                    "value": {
                      "stringValue": "172.23.0.5"
                    }
                  },
                  {
                    "key": "net.peer.name",
                    "value": {
                      "stringValue": "backend"
                    }
                  },
                  {
                    "key": "peer.service",
                    "value": {
                      "stringValue": "backend"
                    }
                  },
                  {
                    "key": "net.peer.ip",
                    "value": {
                      "stringValue": "172.23.0.4"
                    }
                  },
                  {
                    "key": "net.peer.port",
                    "value": {
                      "stringValue": "9000"
                    }
                  }
                ],
                "events": [
                  {
                    "timeUnixNano": "1772164123381183000",
                    "name": "ws",
                    "attributes": []
                  },
                  {
                    "timeUnixNano": "1772164123384030000",
                    "name": "wr",
                    "attributes": []
                  }
                ],
                "status": {
                  "code": "STATUS_CODE_UNSET"
                }
              },
              {
                "traceId": "f321ebb45ffee8b5",
                "spanId": "f321ebb45ffee8b5",
                "name": "post /",
                "kind": "SPAN_KIND_SERVER",
                "startTimeUnixNano": "1772164123378404000",
                "endTimeUnixNano": "1772164123384837000",
                "attributes": [
                  {
                    "key": "http.method",
                    "value": {
                      "stringValue": "POST"
                    }
                  },
                  {
                    "key": "http.path",
                    "value": {
                      "stringValue": "/"
                    }
                  },
                  {
                    "key": "net.host.ip",
                    "value": {
                      "stringValue": "172.23.0.5"
                    }
                  },
                  {
                    "key": "net.peer.ip",
                    "value": {
                      "stringValue": "172.23.0.1"
                    }
                  },
                  {
                    "key": "net.peer.port",
                    "value": {
                      "stringValue": "55480"
                    }
                  }
                ],
                "events": [
                  {
                    "timeUnixNano": "1772164123378496000",
                    "name": "wr",
                    "attributes": []
                  },
                  {
                    "timeUnixNano": "1772164123384602000",
                    "name": "ws",
                    "attributes": []
                  }
                ],
                "status": {
                  "code": "STATUS_CODE_UNSET"
                }
              }
            ]
          }
        ]
      }
    ]
  }
}

```

**Response (Protobuf)**:
When `Accept: application/protobuf` header is set, the response will be in OpenTelemetry Protobuf format.

## Zipkin Trace Conversion

When using the Zipkin backend, the following conversions are applied.

TraceQL filters map onto the Zipkin query: `resource.service.name` and `resource.remote.service` onto the local and
remote service, `name` onto the span name, `duration` onto the span duration, `status = error` onto the `error` tag,
and `span.<key>` (including `span.http.status_code`) onto tag equality. Zipkin spans have no filterable kind and no
`ok` status, so the `intrinsic` scope of `/api/v2/search/tags` lists `name`, `status` and `duration`, and `status`
has the single value `error`. A `q` narrower than a service name makes the tag endpoints sample the newest 50 matching
traces and answer from their matching spans.

### Span Kind Mapping
Zipkin span kinds are mapped to OTLP span kinds:

| Zipkin Span Kind | OTLP Span Kind          |
|------------------|-------------------------|
| `CLIENT`         | `SPAN_KIND_CLIENT`      |
| `SERVER`         | `SPAN_KIND_SERVER`      |
| `PRODUCER`       | `SPAN_KIND_PRODUCER`    |
| `CONSUMER`       | `SPAN_KIND_CONSUMER`    |
| (absent)         | `SPAN_KIND_UNSPECIFIED` |

Zipkin has no `INTERNAL` kind, so those four values and "absent" are the complete set. An OTLP `SPAN_KIND_INTERNAL` span that
was stored through the OTLP receiver's Zipkin conversion has no Zipkin kind and reads back as `SPAN_KIND_UNSPECIFIED`;
the receiver keeps a `span.kind=internal` tag on it, which is searchable as an ordinary tag but is not used for the
`kind` intrinsic. Natively stored OTLP spans keep their original kind, see [OTLP Native Trace](#otlp-native-trace).

### Status Mapping
Zipkin tags are used to derive the OTLP span status in the following priority order:

1. If the `otel.status_code` tag is present, it is parsed directly as the OTLP `StatusCode` (e.g. `STATUS_CODE_OK`, `STATUS_CODE_ERROR`).
2. Otherwise, if the `error` tag equals `true` (case-insensitive), the status is set to `STATUS_CODE_ERROR`.
3. If neither tag is present, the status defaults to `STATUS_CODE_UNSET`.

The `otel.status_description` tag, if present, is used as the status message.

### Endpoint Mapping
Zipkin endpoint fields are mapped to OTLP span attributes:

| Zipkin Field                 | OTLP Attribute                  |
|------------------------------|---------------------------------|
| `localEndpoint.ipv4`         | `net.host.ip`                   |
| `localEndpoint.ipv6`         | `net.host.ip`                   |
| `localEndpoint.port`         | `net.host.port`                 |
| `remoteEndpoint.serviceName` | `net.peer.name`, `peer.service` |
| `remoteEndpoint.ipv4`        | `net.peer.ip`                   |
| `remoteEndpoint.ipv6`        | `net.peer.ip`                   |
| `remoteEndpoint.port`        | `net.peer.port`                 |

### Annotations
Zipkin annotations are converted to OTLP span events. Each annotation becomes a `Span.Event` with `timeUnixNano` (converted from microseconds) and `name` set to the annotation value.

### Instrumentation Scope
All spans within a Zipkin trace carry the instrumentation scope:
```
name: "zipkin-tracer"
version: "0.1.0"
```

## SkyWalking Native Trace Conversion

When using the SkyWalking native backend, the following conversions are applied.

TraceQL filters map onto the trace query: `resource.service.name` onto the service, `resource.instance` onto the
instance, `name` onto the endpoint, `duration` onto the span duration, `status = error` and `status = ok` onto the
trace state, and `span.<key>` (including `span.http.status_code`) onto tag equality. The span type is not a query
condition, so the `intrinsic` scope of `/api/v2/search/tags` lists `name`, `status` and `duration`; `kind`,
`status = unset` and `resource.remote.service` are refused with 400, and `resource.instance` and `name` need
`resource.service.name` because instance and endpoint ids are scoped by service. A `q` narrower
than a service name makes the tag endpoints sample the newest 50 matching traces and answer from their matching
spans; the `span` names offered are cut down to the `searchableTracesTags`, the only tags the trace query can filter.

### Trace ID Encoding

SkyWalking native trace IDs are arbitrary strings that may contain characters outside the hexadecimal alphabet
(for example, `2a2e04e8d1114b14925c04a6321ca26c.38.17739924187687539` includes `.` separators).
OTLP and Grafana Tempo require trace IDs to be pure hex strings.

To satisfy this constraint, every SkyWalking trace ID is encoded by converting each UTF-8 byte of the
original string to two lowercase hex characters:

```
Original: 2a2e04e8d1114b14925c04a6321ca26c.38.17739924187687539
Encoded:  32613265303465386431313134623134393235633034613633323163613236632e33382e3137373339393234313837363837353339
```

- **Encoding**: `traceId.getBytes(UTF-8)` → each byte formatted as `%02x` → concatenated lowercase hex string.
- **Decoding**: hex string → byte array → `new String(bytes, UTF-8)` → original SkyWalking trace ID.

The encoded trace ID is what appears in all API responses (e.g., the `traceID` field in [Search Traces](#search-traces)).
When calling [Query Trace by ID](#query-trace-by-id-v1), use the encoded hex form as the `{traceId}` path parameter.

### Span Kind Mapping
SkyWalking span types are mapped to OTLP span kinds:

| SkyWalking Span Type | OTLP Span Kind          |
|----------------------|-------------------------|
| `Entry`              | `SPAN_KIND_SERVER`      |
| `Exit`               | `SPAN_KIND_CLIENT`      |
| `Local`              | `SPAN_KIND_INTERNAL`    |
| (absent)             | `SPAN_KIND_UNSPECIFIED` |
| (other)              | `SPAN_KIND_INTERNAL`    |

### Status Mapping
The SkyWalking `isError` flag is mapped to OTLP span status:

| SkyWalking `isError` | OTLP Status Code      | OTLP Status Message |
|----------------------|-----------------------|---------------------|
| `true`               | `STATUS_CODE_ERROR`   | `"Error occurred"`  |
| `false`              | `STATUS_CODE_OK`      | (empty)             |

### Span Logs
SkyWalking span logs are converted to OTLP span events. Each `LogEntity` produces one `Span.Event` with:
- `timeUnixNano` converted from the log timestamp (milliseconds → nanoseconds)
- `name` fixed as `"log"`
- `attributes` populated from `log.data` key-value pairs as string attributes

### SpanAttachedEvents
SkyWalking [SpanAttachedEvents](../concepts-and-designs/event.md) are converted to OTLP span events.
Each `SpanAttachedEvent` produces one OTLP `Span.Event` with:
- `timeUnixNano` from `attachedEvent.startTime`
- `name` from `attachedEvent.event`
- `attributes` populated from both `attachedEvent.tags` (string key-value pairs) and `attachedEvent.summary` (numeric key-value pairs, serialised as strings)

### Instrumentation Scope
All spans within a SkyWalking native trace carry the instrumentation scope:
```
name: "skywalking-tracer"
version: "0.1.0"
```

## OTLP Native Trace
The `/otlp` datasource serves the spans that `receiver-otel` stored natively (`otlpTraceStorage: otlp`, see
[OpenTelemetry Trace Format](../setup/backend/otlp-trace.md#native-otlp-mode)). Nothing is converted: a trace is
returned as the `ResourceSpans` the exporters sent, regrouped into one entry per resource and one `ScopeSpans` per
instrumentation scope, with the spans of each scope ordered by start time. Attribute types, events, links, status
messages and the instrumentation scope are exactly what the SDK exported.

The datasource is available on every storage. `coldStage=true` on `/api/search` and `/api/v2/traces/{traceId}`
reads the BanyanDB cold stage instead of the hot and warm stages and needs `start` and `end`.

### TraceQL Field Mapping
| TraceQL field                     | Stored column / index                                                                        |
|-----------------------------------|----------------------------------------------------------------------------------------------|
| `resource.service.name`           | `service_name`, the service name resolved from the resource attributes                       |
| `resource.service.instance.id`    | `service_instance`                                                                           |
| `resource.remote.service`         | `peer_service`, the `peer.service` span attribute                                            |
| `span.otel.scope.name`            | `scope_name`, the instrumentation scope name                                                 |
| `span.peer.service`               | `peer_service`                                                                               |
| `span.service.instance.id`        | `service_instance`                                                                           |
| `span.http.status_code`           | The `http.status_code` attribute, as an ordinary tag condition                               |
| `span.<key>`                      | The `<key>=<value>` tag index, every resource and span attribute of the span is indexed      |
| `name`, `span:name`               | `name`                                                                                       |
| `kind`, `span:kind`               | `kind`. Accepted values: `unspecified`, `internal`, `server`, `client`, `producer`, `consumer` |
| `status`, `span:status`           | `status_code`. Accepted values: `error`, `ok`, `unset`                                        |
| `duration`, `span:duration`       | `duration`, in nanoseconds; TraceQL durations such as `100ms` are converted                  |

Tempo's other scoped intrinsics, `trace:*`, `event:*`, `link:*` and `instrumentation:*`, have no column to filter on
and are rejected with 400 rather than ignored.

### Search Results
As on every datasource, a span set holds only the spans that matched, capped at `spss`, and `serviceStats` counts the
spans and errors per service. The listed attributes are `service.name`, `span.kind` and the keys configured by
`otlpTracesListResultTags`, each with its OTLP type.

### Tag Names and Values
`/api/v2/search/tags` returns `service.name`, `service.instance.id` and `remote.service` under the `resource` scope,
the keys configured by `receiver-otel.default.otlpTraceSearchableTags` plus `otel.scope.name` under the `span` scope,
and `name`, `status`, `kind`, `duration` and their `span:` spellings under `intrinsic`. `scope` narrows the answer to
one scope (`event`, `link` and `instrumentation` are accepted and empty), `limit` caps the number of names per scope,
and the v1 `/api/search/tags` lists the same names without scopes. `service.name` values come from the service catalog, `name`
values from the span-name catalog of the service given in `q`, `remote.service` values from the peer catalog, and the
other span keys from the tag autocomplete index; `limit` caps every value list. A `q` narrower than a service name, for
instance `{ resource.service.name = "frontend" && kind = client }`, switches both endpoints to a sample: the newest 50
matching traces are read and the names or values are taken from the spans in them that match the filter, which is
how Grafana's dependent dropdowns narrow down. Every attribute is indexed, so every key such a sample offers is
filterable. `status` and `kind` are exempt: their dropdowns always list the full enum, so `error` stays selectable
when the newest traces happen to have none.

### Trace by ID
`/api/v2/traces/{traceId}` takes the 32-character lowercase hex trace id, the `traceID` returned by `/api/search`,
and returns `application/protobuf` or `application/json` according to the `Accept` header. Trace, span and parent
span ids are hex in the JSON form.

## Configuration

### Enabling Backends
TraceQL Service supports three backends that can be independently enabled or disabled:

| Configuration Field          | Env Variable                              | Default | Description                                                                                                        |
|------------------------------|-------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------|
| `enableDatasourceZipkin`     | `SW_TRACEQL_ENABLE_DATASOURCE_ZIPKIN`     | `false` | Enable Zipkin-compatible backend                                                                                   |
| `enableDatasourceSkywalking` | `SW_TRACEQL_ENABLE_DATASOURCE_SKYWALKING` | `false` | Enable SkyWalking native backend                                                                                   |
| `enableDatasourceOTLP`       | `SW_TRACEQL_ENABLE_DATASOURCE_OTLP`       | `false` | Enable the OTLP backend, serving spans stored natively by `receiver-otel` with `otlpTraceStorage: otlp`          |

### Context Path
Each backend is served under a separate context path:

| Configuration Field           | Env Variable                              | Default       | Description                         |
|-------------------------------|-------------------------------------------|---------------|-------------------------------------|
| `restContextPathZipkin`       | `SW_TRACEQL_REST_CONTEXT_PATH_ZIPKIN`     | `/zipkin`     | Context path for Zipkin backend     |
| `restContextPathSkywalking`   | `SW_TRACEQL_REST_CONTEXT_PATH_SKYWALKING` | `/skywalking` | Context path for SkyWalking backend |
| `restContextPathOTLP`         | `SW_TRACEQL_REST_CONTEXT_PATH_OTLP`       | `/otlp`       | Context path for OTLP backend       |

### Traces List Result Tags
The tags included in the search result spans can be configured. Only listed tags are returned in `Search Traces` responses.
`service.name`, `span.kind` and `status` (`error`, `ok` or `unset`, so a trace list can show failures without opening
each trace) are always included regardless of this setting.

| Configuration Field              | Env Variable                                     | Default                                                                              |
|----------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------|
| `zipkinTracesListResultTags`     | `SW_TRACEQL_ZIPKIN_TRACES_LIST_RESULT_TAGS`      | `http.method,error`                                                                  |
| `skywalkingTracesListResultTags` | `SW_TRACEQL_SKYWALKING_TRACES_LIST_RESULT_TAGS`  | `http.method,http.status_code,rpc.status_code,db.type,db.instance,mq.queue,mq.topic,mq.broker` |
| `otlpTracesListResultTags`       | `SW_TRACEQL_OTLP_TRACES_LIST_RESULT_TAGS`        | `http.request.method,http.response.status_code`                                      |

### Other Settings

| Configuration Field    | Env Variable                    | Default    | Description                                                                                        |
|------------------------|---------------------------------|------------|----------------------------------------------------------------------------------------------------|
| `restHost`             | `SW_TRACEQL_REST_HOST`          | `0.0.0.0`  | Bind host                                                                                          |
| `restPort`             | `SW_TRACEQL_REST_PORT`          | `3200`     | Bind port                                                                                          |
| `restIdleTimeOut`      | `SW_TRACEQL_REST_IDLE_TIMEOUT`  | `30000`    | HTTP idle timeout in milliseconds                                                                  |
| `restAcceptQueueSize`  | `SW_TRACEQL_REST_QUEUE_SIZE`    | `0`        | HTTP accept queue size (0 = unlimited)                                                             |
| `lookback`             | `SW_TRACEQL_LOOKBACK`           | `86400000` | Default look-back window in milliseconds when no `start` is given, the default end time is current |

Full example in `application.yml`:
```yaml
traceQL:
  selector: ${SW_TRACEQL:default}
  default:
    restHost: ${SW_TRACEQL_REST_HOST:0.0.0.0}
    restPort: ${SW_TRACEQL_REST_PORT:3200}
    enableDatasourceZipkin: ${SW_TRACEQL_ENABLE_DATASOURCE_ZIPKIN:true}
    enableDatasourceSkywalking: ${SW_TRACEQL_ENABLE_DATASOURCE_SKYWALKING:true}
    enableDatasourceOTLP: ${SW_TRACEQL_ENABLE_DATASOURCE_OTLP:true}
    restContextPathZipkin: ${SW_TRACEQL_REST_CONTEXT_PATH_ZIPKIN:/zipkin}
    restContextPathSkywalking: ${SW_TRACEQL_REST_CONTEXT_PATH_SKYWALKING:/skywalking}
    restContextPathOTLP: ${SW_TRACEQL_REST_CONTEXT_PATH_OTLP:/otlp}
    restIdleTimeOut: ${SW_TRACEQL_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_TRACEQL_REST_QUEUE_SIZE:0}
    lookback: ${SW_TRACEQL_LOOKBACK:86400000}
    zipkinTracesListResultTags: ${SW_TRACEQL_ZIPKIN_TRACES_LIST_RESULT_TAGS:http.method,error}
    skywalkingTracesListResultTags: ${SW_TRACEQL_SKYWALKING_TRACES_LIST_RESULT_TAGS:http.method,http.status_code,rpc.status_code,db.type,db.instance,mq.queue,mq.topic,mq.broker}
    otlpTracesListResultTags: ${SW_TRACEQL_OTLP_TRACES_LIST_RESULT_TAGS:http.request.method,http.response.status_code}
```

## Integration with Grafana
***Notice:*** The feature requires version `Grafana 12 or later`.
For more details, refer to [Use Grafana As The UI](../setup/backend/ui-grafana.md).

## Limitations
- Pipeline operations (`|` operator) are not supported
- Aggregate functions (count, avg, max, min, sum) are not supported
- TraceQL metrics, `/api/metrics/query_range` and `/api/metrics/query`, are not supported by any datasource; both answer `501 Not Implemented` with an error body
- Regular expression matching (`=~`, `!~`) is not implemented
- Spanset logical operations (AND, OR between spansets) are not supported
- Event and link scopes are not supported
- Streaming mode is not supported (disable "Streaming" option in Grafana Tempo data source configuration)

## See Also
- [Grafana Tempo TraceQL Documentation](https://grafana.com/docs/tempo/latest/traceql/)
- [OpenTelemetry Protocol Specification](https://opentelemetry.io/docs/reference/specification/protocol/)
- [SkyWalking Trace Query](https://skywalking.apache.org/docs/main/next/en/api/query-protocol/#trace)


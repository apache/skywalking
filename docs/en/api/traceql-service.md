# TraceQL Service
TraceQL ([Trace Query Language](https://grafana.com/docs/tempo/latest/traceql/)) is Grafana Tempo's query language for traces.
TraceQL Service exposes Tempo Querying HTTP APIs including TraceQL expression system and OpenTelemetry Protocol (OTLP) trace format.
Third-party systems or visualization platforms that already support Tempo and TraceQL (such as Grafana), could obtain traces through TraceQL Service.

SkyWalking supports two types of traces: SkyWalking native traces and Zipkin-compatible traces. The TraceQL Service converts both trace formats to OpenTelemetry Protocol (OTLP) format to provide compatibility with Grafana Tempo and TraceQL queries.

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
- [x] **Intrinsic Fields**: Built-in trace fields
  - [x] `duration` - Trace duration with comparison operators (supports units: us/µs, ms, s, m, h. Default unit: microseconds. Minimum: microseconds, Maximum: hours)
  - [x] `name` - Span name
  - [x] `status` - Span status
  - [ ] `kind` - Span kind
- [x] **Comparison Operators**: 
  - [x] `=` - Equals
  - [x] `>` - Greater than (for duration)
  - [x] `>=` - Greater than or equal (for duration)
  - [x] `<` - Less than (for duration)
  - [x] `<=` - Less than or equal (for duration)
- [ ] **Spanset Logical Operations**: AND/OR between spansets (e.g., `{...} AND {...}`)
- [ ] **Pipeline Operations**: `|` operator for aggregations
- [ ] **Aggregate Functions**: count(), avg(), max(), min(), sum()
- [ ] **Regular Expression**: `=~` and `!~` operators

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
{status="STATUS_CODE_OK"}
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
| q         | TraceQL query to filter       | yes      |
| scope     | Scope to filter tags          | yes      |
| limit     | Maximum number of tags        | yes      |
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
| tagName   | Name of the tag               | no       |
| q         | TraceQL filter query          | yes      |
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

| Parameter   | Definition                          | Optional      |
|-------------|-------------------------------------|---------------|
| q           | TraceQL query                       | yes           |
| tags        | Deprecated tag query format         | yes           |
| minDuration | Minimum trace duration              | yes           |
| maxDuration | Maximum trace duration              | yes           |
| limit       | Maximum number of traces to return  | yes           |
| start       | Start timestamp (seconds)           | yes            |
| end         | End timestamp (seconds)             | yes            |
| spss        | Spans per span set                  | not supported |

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
                    "stringValue": "SERVER"
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
                    "stringValue": "CLIENT"
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

**Response (JSON)**:
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

## Configuration

### Context Path
TraceQL Service supports custom context paths for different trace backends:

- **Zipkin Backend**: `/zipkin` - Queries Zipkin-compatible traces and converts to OTLP format
- **SkyWalking Native**: `/skywalking` - Queries SkyWalking native traces and converts to OTLP format

Configuration in `application.yml`:
```yaml
tempo-query:
  zipkinContextPath: /zipkin
  skyWalkingContextPath: /skywalking
```

Both backends convert their respective trace formats to OpenTelemetry Protocol (OTLP) format for TraceQL compatibility.

## Integration with Grafana

### Add Tempo Data Source
1. In Grafana, go to Configuration → Data Sources
2. Add a new Tempo data source
3. Set the URL to: `http://<oap-host>:<port>/zipkin` (for Zipkin-compatible traces) or `http://<oap-host>:<port>/skywalking` (for SkyWalking native traces)
4. **Important**: Disable the "Streaming" option as it is not currently supported
5. Save and test the connection

### Query Traces
- Use TraceQL queries in Grafana Explore
- View trace details with OTLP visualization
- Search traces by service, span, duration, status and tags

## Limitations
- Pipeline operations (`|` operator) are not supported
- Aggregate functions (count, avg, max, min, sum) are not supported
- Regular expression matching (`=~`, `!~`) is not implemented
- Spanset logical operations (AND, OR between spansets) are not supported
- Event and link scopes are not supported
- Streaming mode is not supported (disable "Streaming" option in Grafana Tempo data source configuration)

## See Also
- [Grafana Tempo TraceQL Documentation](https://grafana.com/docs/tempo/latest/traceql/)
- [OpenTelemetry Protocol Specification](https://opentelemetry.io/docs/reference/specification/protocol/)
- [SkyWalking Trace Query](https://skywalking.apache.org/docs/main/next/en/api/query-protocol/)


# LogQL Service
LogQL ([Log Query Language](https://grafana.com/docs/loki/latest/logql/)) is Grafana Loki’s PromQL-inspired query language. 
LogQL Service exposes Loki Querying HTTP APIs including the bundled LogQL expression system.
Third-party systems or visualization platforms that already support LogQL (such as Grafana), could obtain logs through LogQL Service.

As Skywalking log mechanism is different from Loki(metric extract, storage, etc.), the LogQL implemented by Skywalking won't be a full features LogQL.

## Details Of Supported LogQL
The following doc describes the details of the supported protocol and compared it to the LogQL official documentation.
If not mentioned, it will not be supported by default.

### [Log queries](https://grafana.com/docs/loki/latest/logql/log_queries/)
The picture bellow is LogQL syntax in log queries:
<img src="https://grafana.com/docs/loki/latest/logql/log_queries/query_components.png"/>

The expression supported by LogQL it is composed of the following parts (expression with [✅] is Skywalking supported):
- [x] `stream selector`：The stream selector determines which log streams to include in a query’s results by labels.
- [x] `line filter`: The line filter expression does a grep over the logs from the matching log streams.
- [ ] `label filter`: Label filter expression allows filtering log line using their original and extracted labels.
- [ ] `parser`: Parser expression can parse and extract labels from the log content. Those extracted labels can then be used by label filter expressions.
- [ ] `line formate`: The line format expression can rewrite the log line content by using the text/template format.
- [ ] `labels formate`: The label format expression can rename, modify or add labels.
- [ ] `drop labels`: The drop expression will drop the given labels in the pipeline.

The stream selector operator supported by LogQL is composed of the following (operator with [✅] is Skywalking supported):
- [x] `=`: exactly equal
- [ ] `!=`: not equal
- [ ] `=~`: regex matches
- [ ] `!~`: regex does not match

The filter operator supported by LogQL is composed of the following (operator with [✅] is Skywalking supported):
- [x] `|=`: Log line contains string
- [x] `!=`: Log line does not contain string
- [ ] `|~`: Log line contains a match to the regular expression
- [ ] `!~`: Log line does not contain a match to the regular expression

Here are some typical expressions used in Skywalking log query:
```
# query service instance logs with specified traceId
{service="$service", service_instance="$service_instance", trace_id="$trace_id"}
```
```
# query service instance logs contains keyword in content
{service="$service", service_instance="$service_instance"} |= "$keyword_contains"
```
```
# query service instance logs not contains keyword in content
{service="$service", service_instance="$service_instance"} != "$keyword_not_contains"
```
```
# query service instance logs contains A keyword but not contains B keyword in content
{service="$service", service_instance="$service_instance"} |= "$keyword_contains" != "$keyword_not_contains"
```

### [Metric queries](https://grafana.com/docs/loki/latest/logql/metric_queries/)
Metric queries is used to calculate metrics from logs in Loki. 
In Skywalking, it is recommended to use LAL([Log Analysis Language](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/lal/)).So metric queries LogQL won't be supported in Skywalking.

## Details Of Supported Http Query API
### [List Labels](https://grafana.com/docs/loki/latest/api/#list-labels-within-a-range-of-time)
Query log tags within a range of time.
It is different from Loki. In loki, this api query all labels used in stream selector, 
but in Skywalking, this api only for log tags query. Others metadata (service、service_instance、endpoint) query provided by [PromQL Service](https://skywalking.apache.org/docs/main/next/en/api/promql-service/).

```text
GET /loki/api/v1/labels
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| start     | start timestamp in nanoseconds | no       |
| end       | end timestamp in nanoseconds   | no       |

For example:
```text
/loki/api/v1/labels?start=1690947455457000000&end=1690947671936000000
```

Result:
```json
{
    "status": "success",
    "data": [
        "level"
    ]
}
```
### [List Label values](https://grafana.com/docs/loki/latest/api/#list-label-values-within-a-range-of-time)
Query log tag values of tag within a range of time.

```text
GET /loki/api/v1/label/<label_name>/values
```

| Parameter | Definition                     | Optional |
|-----------|--------------------------------|----------|
| start     | start timestamp in nanoseconds | no       |
| end       | end timestamp in nanoseconds   | no       |

For example:
```text
/loki/api/v1/label/level/values?start=1690947455457000000&end=1690947671936000000
```

Result:
```json
{
  "status": "success",
  "data": [
    "INFO",
    "WARN",
    "ERROR"
  ]
}
```

### [Range queries](https://grafana.com/docs/loki/latest/api/#query-loki-over-a-range-of-time)
Query logs within a range of time with LogQL expression.

```text
GET /loki/api/v1/query_range
```

| Parameter | Definition                              | Optional |
|-----------|-----------------------------------------|----------|
| query     | logql expression                        | no       |
| start     | start timestamp in nanoseconds          | no       |
| end       | end timestamp in nanoseconds            | no       |
| limit     | numbers of log line returned in a query | no       |
| direction | log order,FORWARD or BACKWARD           | no       |

For example:
```text
/api/v1/query_range?query={service='agent::songs'}&start=1690947455457000000&end=1690947671936000000&limit=100&direction=BACKWARD
```

Result:
```json
{
  "status": "success",
  "data": {
    "resultType": "streams",
    "result": [
      {
        "stream": {
          "service": "agent::songs",
          "service_instance": "instance1",
          "endpoint": "xxx",
          "trace_id": "xxx"
        },
        "values": [
          [
            "1690947671936000000",
            "foo"
          ],
          [
            "1690947455457000000",
            "bar"
          ]
        ]
      },
      {
        "stream": {
          "service": "agent::songs",
          "service_instance": "instance2",
          "endpoint": "xxx",
          "trace_id": "xxx"
        },
        "values": [
          [
            "1690947671936000000",
            "foo"
          ],
          [
            "1690947455457000000",
            "bar"
          ]
        ]
      }
    ]
  }
}
```
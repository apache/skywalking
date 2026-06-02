# Status API

The Status API is a set of read-only HTTP endpoints for inspecting cluster
membership, alarm runtime state, effective configuration / TTL settings,
and per-query debug traces. Hosted by the `status` feature module on the
admin-server REST host (default `17128`), alongside `/ui-management/*`,
`/inspect/*`, `/dsl-debugging/*`, and `/runtime/rule/*`. One handler —
`/status/config/ttl` — is also bound on the public REST host
(default `12800`) so ecosystem tools that discover TTL via REST before
issuing `/graphql` can fetch it without being aware of the admin port.

## Hosting

`status` registers all handlers on the admin-server REST host (default port
`17128`). Both `status` and `admin-server` are enabled by default, so the
surface is reachable out of the box. The admin port is gateway-protected per
the [admin-server security notice](readme.md#security-notice). To disable
status explicitly, unset `SW_STATUS`:

```bash
export SW_STATUS=                       # disable
export SW_STATUS=default                # default (enabled)
```

## Configuration

```yaml
status:
  selector: ${SW_STATUS:default}
  default:
    keywords4MaskingSecretsOfConfig: ${SW_DEBUGGING_QUERY_KEYWORDS_FOR_MASKING_SECRETS:user,password,trustStorePass,keyStorePass,token,accessKey,secretKey,authentication}
```

`keywords4MaskingSecretsOfConfig` is consumed by `/debugging/config/dump`
to redact configuration values whose key contains any listed substring.

## Endpoints

### /status/cluster/nodes

Returns the OAP cluster peer list as the cluster module sees it. Useful
for confirming that every node has joined and is reporting back.

The OAP cluster is a set of OAP servers that work together to provide a scalable and reliable service. The OAP cluster supports [various cluster coordinators](../backend-cluster.md) to manage membership and communication. This API lets you query the node list from each OAP node’s perspective. If the cluster coordinator doesn’t work properly, the node list may be incomplete or incorrect, so we recommend checking it when setting up a cluster.

- HTTP GET method.

```bash
curl http://oap:17128/status/cluster/nodes
```

```json
{
  "nodes": [
    {
      "host": "10.0.12.23",
      "port": 11800,
      "self": true
    },
    {
      "host": "10.0.12.25",
      "port": 11800,
      "self": false
    },
    {
      "host": "10.0.12.37",
      "port": 11800,
      "self": false
    }
  ]
}
```

The `nodes` list all the nodes in the cluster. The size of the list should be exactly same as your cluster setup. The `host` and `port` are the address of the OAP node, which are used for OAP nodes communicating with each other. The `self` is a flag to indicate whether the node is the current node, others are remote nodes.

### Alarm Runtime Status

OAP calculates the alarm conditions in the memory based on the alarm rules and the metrics data. If the OAP cluster has multiple instances, each instance will calculate the alarm conditions independently. You can query from any OAP instance to get the all instances' alarm running status.

The following APIs are exposed to make the alerting running kernel visible.

#### /status/alarm/rules

Return the list of alarm running rules.

- HTTP GET method.

```json
{
  "oapInstances": [
    {
      "address": "127.0.0.1_11800",
      "status": {
        "ruleList": [
          {
            "id": "service_percentile_rule"
          },
          {
            "id": "service_resp_time_rule"
          }
        ]
      }
    },
    {
      "address": "127.0.0.1_11801",
      "status": {
        "ruleList": [
          {
            "id": "service_percentile_rule"
          },
          {
            "id": "service_resp_time_rule"
          }
        ]
      }
    }
  ]
}
```

#### /status/alarm/rules/{ruleId}

Return the detailed information of the alarm running rule.

- HTTP GET method.

```json
{
  "oapInstances": [
    {
      "address": "127.0.0.1_11800",
      "status": {
        "ruleId": "service_resp_time_rule",
        "expression": "sum(service_resp_time > 1000) >= 1",
        "period": 10,
        "silencePeriod": 10,
        "recoveryObservationPeriod": 2,
        "additionalPeriod": 0,
        "includeEntityNames": [],
        "excludeEntityNames": [],
        "includeEntityNamesRegex": "",
        "excludeEntityNamesRegex": "",
        "runningEntities": [
          {
            "scope": "SERVICE",
            "name": "mock_b_service",
            "formattedMessage": "Service mock_b_service response time is more than 1000ms of last 10 minutes"
          }
        ],
        "tags": [
          {
            "key": "level",
            "value": "WARNING"
          }
        ],
        "hooks": [
          "webhook.default",
          "wechat.default"
        ],
        "includeMetrics": [
          "service_resp_time"
        ]
      }
    },
    {
      "address": "127.0.0.1_11801",
      "status": {
        "ruleId": "service_resp_time_rule",
        "expression": "sum(service_resp_time > 1000) >= 1",
        "period": 10,
        "silencePeriod": 10,
        "recoveryObservationPeriod": 2,
        "additionalPeriod": 0,
        "includeEntityNames": [],
        "excludeEntityNames": [],
        "includeEntityNamesRegex": "",
        "excludeEntityNamesRegex": "",
        "runningEntities": [
          {
            "scope": "SERVICE",
            "name": "mock_a_service",
            "formattedMessage": "Service mock_a_service response time is more than 1000ms of last 10 minutes."
          },
          {
            "scope": "SERVICE",
            "name": "mock_c_service",
            "formattedMessage": "Service mock_c_service response time is more than 1000ms of last 10 minutes."
          }
        ],
        "tags": [
          {
            "key": "level",
            "value": "WARNING"
          }
        ],
        "hooks": [
          "webhook.default",
          "wechat.default"
        ],
        "includeMetrics": [
          "service_resp_time"
        ]
      }
    }
  ]
}
```

- `additionalPeriod` is the additional period if the expression includes the [increase/rate function](../../../api/metrics-query-expression.md#trend-operation). This additional period is used to enlarge the window size for calculating the trend value.
- `runningEntities` are the entities that have metrics data and are evaluated by the alarm rule.
- `formattedMessage` is the rendered message based on the rule’s message template for each affected running entity.

#### `/status/alarm/{ruleId}/{entityName}`

Return the running context of the alarm rule.

- HTTP GET method.

```json
{
  "oapInstances": [
    {
      "address": "127.0.0.1_11800",
      "status": {
        "ruleId": "service_resp_time_rule",
        "expression": "sum(service_resp_time > 1000) >= 1",
        "endTime": "2025-11-19T15:20:00.000",
        "additionalPeriod": 0,
        "size": 10,
        "silencePeriod": 10,
        "recoveryObservationPeriod": 0,
        "silenceCountdown": 10,
        "recoveryObservationCountdown": 0,
        "currentState": "FIRING",
        "entityName": "mock_b_service",
        "windowValues": [
          {
            "index": 0,
            "metrics": []
          },
          {
            "index": 1,
            "metrics": []
          },
          {
            "index": 2,
            "metrics": []
          },
          {
            "index": 3,
            "metrics": []
          },
          {
            "index": 4,
            "metrics": []
          },
          {
            "index": 5,
            "metrics": []
          },
          {
            "index": 6,
            "metrics": []
          },
          {
            "index": 7,
            "metrics": []
          },
          {
            "index": 8,
            "metrics": [
              {
                "name": "service_resp_time",
                "timeBucket": 202511191519,
                "value": "6000"
              }
            ]
          },
          {
            "index": 9,
            "metrics": []
          }
        ],
        "mqeMetricsSnapshot": {
          "service_resp_time": "[{\"metric\":{\"labels\":[]},\"values\":[{\"id\":\"202511191511\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191512\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191513\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191514\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191515\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191516\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191517\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191518\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191519\",\"doubleValue\":6000.0,\"isEmptyValue\":false},{\"id\":\"202511191520\",\"doubleValue\":0.0,\"isEmptyValue\":true}]}]"
        },
        "lastAlarmTime": 1763536823628,
        "lastAlarmMessage": "Service mock_b_service response time is more than 1000ms of last 10 minutes.",
        "lastAlarmMqeMetricsSnapshot": {
          "service_resp_time": "[{\"metric\":{\"labels\":[]},\"values\":[{\"id\":\"202511191511\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191512\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191513\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191514\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191515\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191516\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191517\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191518\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202511191519\",\"doubleValue\":6000.0,\"isEmptyValue\":false},{\"id\":\"202511191520\",\"doubleValue\":0.0,\"isEmptyValue\":true}]}]"
        }
      }
    },
    {
      "address": "127.0.0.1_11801",
      "status": {
        "ruleId": "service_resp_time_rule",
        "expression": "sum(service_resp_time > 1000) >= 1",
        "additionalPeriod": 0,
        "size": 0,
        "silenceCountdown": 0,
        "recoveryObservationCountdown": 0,
        "windowValues": [],
        "lastAlarmTime": 0
      }
    }
  ]
}
```

`size` is the window size. Equal to the `period + additionalPeriod`. `silenceCountdown` is the countdown of the silence period. -1 means silence countdown is not running. `recoveryObservationCountdown` is the countdown of the recovery observation period. `windowValues` is the original metrics data when the metrics come in. The `index` is the index of the window, starting from 0. `mqeMetricsSnapshot` is the current metrics data in the MQE format which is generated when executing the checking. These data will be calculated according to the expression. `lastAlarmTime` is the last time when the alarm is triggered. It will be reset to 0 when the alarm recovers. `lastAlarmMessage` is the last alarm message when the alarm is triggered. `lastAlarmMqeMetricsSnapshot` is the metrics data snapshot in the MQE format when the last alarm is triggered.

#### Get Errors When Querying Status from OAP Instances

If some errors occur when querying the status from OAP instances, the error messages will be returned.

```json
{
  "oapInstances": [
    {
      "address": "127.0.0.1_11800",
      "status": {
        "ruleList": [
          {
            "id": "service_percentile_rule"
          },
          {
            "id": "service_resp_time_rule"
          }
        ]
      }
    },
    {
      "address": "127.0.0.1_11801",
      "errorMsg": "UNAVAILABLE: io exception"
    }
  ]
}
```

### /status/config/ttl

Returns the effective TTL configuration the OAP loaded at boot.
**Reachable on both ports** — `:17128` (admin) and `:12800` (public) —
so ecosystem tools can call it without admin-port knowledge. Every
other `/status/*` handler is admin-only.

Time To Live (TTL) mechanism has different behaviors according to different storage implementations. By default, the core module provides two TTL configurations: [`recordDataTTL` and `metricsDataTTL`](../ttl.md). But some storage implementations could override these settings and provide its own TTL configurations, for example, BanyanDB provides its native TTL mechanism to support [progressive TTL](../../../banyandb/ttl.md) feature and [Data Lifecycle Stages(Hot/Warm/Cold)](../../../banyandb/stages.md) feature.

This API is used to get the unified and effective TTL configurations.

- HTTP GET method.

```
curl -X GET "http://oap:17128/status/config/ttl"
```

```
# Metrics TTL includes the definition of the TTL of the metrics-ish data in the storage,
# e.g.
# 1. The metadata of the service, instance, endpoint, topology map, etc.
# 2. Generated metrics data from OAL and MAL engines.
# 3. Banyandb storage provides Data Lifecycle Stages(Hot/Warm/Cold).
#
# TTLs for each granularity metrics are listed separately.
#
metadata=7
# Cover hot and warm data for BanyanDB.
metrics.minute=7
metrics.hour=15
metrics.day=15
# Cold data, '-1' represents no cold stage data.
metrics.minute.cold=-1
metrics.hour.cold=-1
metrics.day.cold=-1

# Records TTL includes the definition of the TTL of the records data in the storage,
# Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.
# Super dataset of records are traces and logs, which volume should be much larger.
#
# Cover hot and warm data for BanyanDB.
records.normal=3
records.trace=10
records.zipkinTrace=3
records.log=3
records.browserErrorLog=3
# Cold data, '-1' represents no cold stage data.
records.normal.cold=-1
records.trace.cold=30
records.zipkinTrace.cold=-1
records.log.cold=-1
records.browserErrorLog.cold=-1
```

This API also provides the response in JSON format, which is more friendly for programmatic usage.

```
curl -X GET "http://oap:17128/status/config/ttl" \
       -H "Accept: application/json"
```

```json
{
  "metrics": {
    "minute": 7,
    "hour": 15,
    "day": 15,
    "coldMinute": -1,
    "coldHour": -1,
    "coldDay": -1
  },
  "records": {
    "normal": 3,
    "trace": 10,
    "zipkinTrace": 3,
    "log": 3,
    "browserErrorLog": 3,
    "coldNormal": -1,
    "coldTrace": 30,
    "coldZipkinTrace": -1,
    "coldLog": -1,
    "coldBrowserErrorLog": -1
  }
}
```

### /debugging/config/dump

Dumps the effective configuration that was applied at boot. Values whose
key contains any substring listed in `keywords4MaskingSecretsOfConfig` are
redacted. Output is YAML-shaped key=value lines.

The Inspect API uses this endpoint as its REST-URL discovery primitive —
clients parse the dump for `core.restHost` / `core.restPort` (or the
sharing-server overrides) once at session start to learn where the public
GraphQL / MQE surface lives.

### /debugging/query/...

Runs a named query path with debug tracing enabled and returns the
captured DAO / storage spans alongside the result. Useful for diagnosing
why a query is slow or returning unexpected data.

| URI | Purpose |
|---|---|
| `/debugging/query/mqe` | Run an MQE expression with tracing. |
| `/debugging/query/trace/queryBasicTraces` | Trace search brief. |
| `/debugging/query/trace/queryTrace` | Trace detail. |
| `/debugging/query/zipkin/api/v2/traces` | Zipkin compat brief. |
| `/debugging/query/zipkin/api/v2/trace` | Zipkin compat detail. |
| `/debugging/query/topology/getGlobalTopology` | Global topology debug. |
| `/debugging/query/topology/getServicesTopology` | Per-service topology debug. |
| `/debugging/query/topology/getServiceInstanceTopology` | Per-instance topology debug. |
| `/debugging/query/topology/getEndpointDependencies` | Endpoint dependencies debug. |
| `/debugging/query/topology/getProcessTopology` | Process topology debug. |
| `/debugging/query/log/queryLogs` | Log query debug. |

The query parameters mirror the corresponding GraphQL inputs (consult the
schema definitions under
`oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol`).

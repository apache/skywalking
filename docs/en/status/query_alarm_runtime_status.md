# Get Alarm Runtime Status

OAP calculates the alarm conditions in the memory based on the alarm rules and the metrics data.
If the OAP cluster has multiple instances, each instance will calculate the alarm conditions independently.
You can query from any OAP instance to get the all instances' alarm running status.

The following APIs are exposed to make the alerting running kernel visible.

## Get Alarm Running Rules

Return the list of alarm running rules.

- URL, `http://{core restHost}:{core restPort}/status/alarm/rules`
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

## Get Alarm Running Rule Info

Return the detailed information of the alarm running rule.

- URL, `http://{core restHost}:{core restPort}/status/alarm/rules/{ruleId}`
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

- `additonalPeriod` is the additional period if the expression includes the [increase/rate function](../api/metrics-query-expression.md#trend-operation).
This additional period is used to enlarge window size for calculating the trend value.
- `runningEntities` is the entities that have metrics data and being calculated by the alarm rule.
- `formattedMessages` is the result message according to the message template and the affected running entities.

## Get Alarm Running Context

Return the running context of the alarm rule.

- URL, `http://{core restHost}:{core restPort}/status/alarm/{ruleId}/{entityName}`
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
        "lastAlarmTime": "1763536823628",
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
`size` is the window size. Equal to the `period + additionalPeriod`.
`silenceCountdown` is the countdown of the silence period. -1 means silence countdown is not running.
`recoveryObservationCountdown` is the countdown of the recovery observation period.
`windowValues` is the original metrics data when the metrics come in. The `index` is the index of the window, starting from 0.
`mqeMetricsSnapshot` is the current metrics data in the MQE format which is generated when executing the checking. 
These data will be calculated according to the expression.
`lastAlarmTime` is the last time when the alarm is triggered. It will be reset to 0 when the alarm recovers.
`lastAlarmMessage` is the last alarm message when the alarm is triggered.
`lastAlarmMqeMetricsSnapshot` is the metrics data snapshot in the MQE format when the last alarm is triggered.

## Get Errors When Querying Status from OAP Instances

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
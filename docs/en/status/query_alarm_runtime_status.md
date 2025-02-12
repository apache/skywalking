# Get Alarm Runtime Status

OAP calculates the alarm conditions in the memory based on the alarm rules and the metrics data.
The following APIs are exposed to make the alerting running kernel visible.

## Get Alarm Running Rules

Return the list of alarm running rules.

- URL, `http://{core restHost}:{core restPort}/status/alarm/rules`
- HTTP GET method.

```json
{
  "ruleNames": [
    "service_percentile_rule",
    "service_resp_time_rule"
  ]
}
```

## Get Alarm Running Rule Info

Return the detailed information of the alarm running rule.

- URL, `http://{core restHost}:{core restPort}/status/alarm/rules/{ruleName}`
- HTTP GET method.

```json
{
  "ruleName": "service_resp_time_rule",
  "expression": "sum(service_resp_time > baseline(service_resp_time,upper)) >= 1",
  "period": 10,
  "silentPeriod": 10,
  "additonalPeriod": 0,
  "includeNames": [
    "mock_a_service",
    "mock_b_service",
    "mock_c_service"
  ],
  "excludeNames": [],
  "includeNamesRegex": "",
  "excludeNamesRegex": "",
  "affectedEntities": [
    {
      "scope": "SERVICE",
      "name": "mock_b_service"
    },
    {
      "scope": "SERVICE",
      "name": "mock_a_service"
    },
    {
      "scope": "SERVICE",
      "name": "mock_c_service"
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
  ],
  "formattedMessages": [
    {
      "mock_b_service": "Response time of service mock_b_service is more than upper baseline in 1 minutes of last 10 minutes."
    },
    {
      "mock_a_service": "Response time of service mock_a_service is more than upper baseline in 1 minutes of last 10 minutes."
    },
    {
      "mock_c_service": "Response time of service mock_c_service is more than upper baseline in 1 minutes of last 10 minutes."
    }
  ]
}
```

- `additonalPeriod` is the additional period if the expression includes the [increase/rate function](../api/metrics-query-expression.md#trend-operation).
This additional period is used to enlarge window size for calculating the trend value.
- `affectedEntities` is the entities that have metrics data and being calculated by the alarm rule.
- `formattedMessages` is the result message according to the message template and the affected entities.

## Get Alarm Running Context

Return the running context of the alarm rule.

- URL, `http://{core restHost}:{core restPort}/status/alarm/{ruleName}/{entityName}`
- HTTP GET method.

```json
{
  "expression": "sum(service_resp_time > baseline(service_resp_time,upper)) >= 1",
  "endTime": "2025-02-12T13:39:00.000",
  "additionalPeriod": 0,
  "size": 10,
  "silenceCountdown": 10,
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
      "metrics": [
        {
          "timeBucket": 202502121437,
          "name": "service_resp_time",
          "value": "6000"
        }
      ]
    },
    {
      "index": 8,
      "metrics": []
    },
    {
      "index": 9,
      "metrics": []
    }
  ],
  "mqeMetricsSnapshot": {
    "service_resp_time": "[{\"metric\":{\"labels\":[]},\"values\":[{\"id\":\"202502121430\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121431\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121432\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121433\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121434\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121435\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121436\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121437\",\"doubleValue\":6000.0,\"isEmptyValue\":false},{\"id\":\"202502121438\",\"doubleValue\":0.0,\"isEmptyValue\":true},{\"id\":\"202502121439\",\"doubleValue\":0.0,\"isEmptyValue\":true}]}]",
    "baseline(service_resp_time,upper)": "[{\"metric\":{\"labels\":[]},\"values\":[{\"id\":\"202502121430\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121431\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121432\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121433\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121434\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121435\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121436\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121437\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121438\",\"doubleValue\":10.0,\"isEmptyValue\":false},{\"id\":\"202502121439\",\"doubleValue\":10.0,\"isEmptyValue\":false}]}]"
  }
}
```
`size` is the window size. Equal to the `period + additionalPeriod`.
`silenceCountdown` is the countdown of the silence period. -1 means silence countdown is not running.
`windowValues` is the original metrics data. The `index` is the index of the window, starting from 0.
`mqeMetricsSnapshot` is the metrics data in the MQE format. When checking conditions, these data will be calculated according to the expression.

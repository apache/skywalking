# Alarm
Alarm core is driven a collection of rules, which are defined in `config/alarm-settings.yml`.
There are two parts in alarm rule definition.
1. Alarm rules. They define how metric alarm should be triggered, what conditions should be considered.
1. Webhooks. The list of web service endpoint, which should be called after the alarm is triggered.

## Rules
Alarm rule is constituted by following keys
- **Rule name**. Unique name, show in alarm message. Must end with `_rule`.
- **Indicator name**. A.K.A. metric name in oal script. Only long, double, int types are supported. See
[List of all potential metric name](#list-of-all-potential-metric-name).
- **Include names**. The following entity names are included in this rule. Such as Service name,
endpoint name.
- **Threshold**. The target value.
- **OP**. Operator, support `>`, `<`, `=`. Welcome to contribute all OPs.
- **Period**. How long should the alarm rule should be checked. This is a time window, which goes with the
backend deployment env time.
- **Count**. In the period window, if the number of **value**s over threshold(by OP), reaches count, alarm
should send.
- **Silence period**. After alarm is triggered in Time-N, then keep silence in the **TN -> TN + period**.
By default, it is as same as **Period**, which means in a period, same alarm(same ID in same 
indicator name) will be trigger once. 


```yaml
rules:
  # Rule unique name, must be ended with `_rule`.
  endpoint_percent_rule:
    # Indicator value need to be long, double or int
    indicator-name: endpoint_percent
    threshold: 75
    op: <
    # The length of time to evaluate the metric
    period: 10
    # How many times after the metric match the condition, will trigger alarm
    count: 3
    # How many times of checks, the alarm keeps silence after alarm triggered, default as same as period.
    silence-period: 10
    
  service_percent_rule:
    indicator-name: service_percent
    # [Optional] Default, match all services in this indicator
    include-names:
      - service_a
      - service_b
    threshold: 85
    op: <
    period: 10
    count: 4
```

## Default alarm rules
We provided a default `alarm-setting.yml` in our distribution only for convenience, which including following rules
1. Service average response time over 1s in last 3 minutes.
1. Service success rate lower than 80% in last 2 minutes.
1. Service 90% response time is lower than 1000ms in last 3 minutes
1. Service Instance average response time over 1s in last 2 minutes.
1. Endpoint average response time over 1s in last 2 minutes.
 


## List of all potential metric name
The metric names are defined in official [OAL scripts](../../guides/backend-oal-scripts.md), right now 
metric from **Service**, **Service Instance**, **Endpoint** scopes could be used in Alarm, we will extend in further versions. 

Submit issue or pull request if you want to support any other scope in alarm.

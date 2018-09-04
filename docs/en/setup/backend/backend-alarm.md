# Alarm
Alarm core is driven a collection of rules, which are defined in `config/alarm-settings.yml`.
There are two parts in alarm rule definition.
1. Alarm rules. They define how metric alarm should be triggered, what conditions should be considered.
1. Webhooks. The list of web service endpoint, which should be called after the alarm is triggered.

## Rules
Alarm rule is constituted by following keys
- **Rule name**. Unique name, show in alarm message. Must end with `_rule`.
- **Indicator name**。A.K.A. metric name in oal script. Only long, double, int types are supported. See
[List of all potential indicator](#list-of-all-potential-indicator-name).
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
```


## List of all potential indicator name

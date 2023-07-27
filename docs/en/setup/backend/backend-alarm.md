# Alerting
Alerting mechanism measures system performance according to the metrics of services/instances/endpoints from different layers.
Alerting kernel is an in-memory, time-window based queue.

The alerting core is driven by a collection of rules defined in `config/alarm-settings.yml.`
There are three parts to alerting rule definitions.
1. [alerting rules](#rules). They define how metrics alerting should be triggered and what conditions should be considered.
1. [hooks](#hooks). The list of hooks, which should be called after an alerting is triggered.

## Entity name
Defines the relation between scope and entity name.
- **Service**: Service name
- **Instance**: {Instance name} of {Service name}
- **Endpoint**: {Endpoint name} in {Service name}
- **Database**: Database service name
- **Service Relation**: {Source service name} to {Dest service name}
- **Instance Relation**: {Source instance name} of {Source service name} to {Dest instance name} of {Dest service name}
- **Endpoint Relation**: {Source endpoint name} in {Source Service name} to {Dest endpoint name} in {Dest service name}

## Rules
**There are two types of rules: individual rules and composite rules. A composite rule is a combination of individual rules.**
### Individual rules
An alerting rule is made up of the following elements:
- **Rule name**. A unique name shown in the alarm message. It must end with `_rule`.
- **Metrics name**. This is also the metrics name in the OAL script. Only long, double, int types are supported. See the
[list of all potential metrics name](#list-of-all-potential-metrics-name). Events can also be configured as the source
of Alarm. Please refer to [the event doc](../../concepts-and-designs/event.md) for more details.
- **Include names**. Entity names that are included in this rule. Please follow the [entity name definitions](#entity-name).
- **Exclude names**. Entity names that are excluded from this rule. Please follow the [entity name definitions](#entity-name).
- **Include names regex**. A regex that includes entity names. If both include-name list and include-name regex are set, both rules will take effect.
- **Exclude names regex**. A regex that excludes entity names. Both rules will take effect if both include-label list and include-label regex are set.
- **Include labels**. Metric labels that are included in this rule.
- **Exclude labels**. Metric labels that are excluded from this rule.
- **Include labels regex**. A regex that includes labels. If both include-label list and include-label regex are set, both rules will take effect.
- **Exclude labels regex**. A regex that excludes labels. Both rules will take effect if both exclude-label list and exclude-label regex are set.
- **Tags**. Tags are key/value pairs that are attached to alarms. Tags are used to specify distinguishing attributes of alarms that are meaningful and relevant to users. If you want to make these tags searchable on the SkyWalking UI, you may set the tag keys in `core/default/searchableAlarmTags` or through the system environment variable `SW_SEARCHABLE_ALARM_TAG_KEYS`. The key `level` is supported by default.

*Label settings are required by the meter system. They are used to store metrics from the label-system platform, such as Prometheus, Micrometer, etc.
The four label settings mentioned above must implement `LabeledValueHolder`.*

- **Threshold**. The target value. 
For multiple-value metrics, such as **percentile**, the threshold is an array. It is described as:  `value1, value2, value3, value4, value5`.
Each value may serve as the threshold for each value of the metrics. Set the value to `-` if you do not wish to trigger the Alarm by one or more of the values.  
For example, in **percentile**, `value1` is the threshold of P50, and `-, -, value3, value4, value5` means that there is no threshold for P50 and P75 in the percentile alarm rule.
- **OP**. The operator. It supports `>`, `>=`, `<`, `<=`, `==`, `!=`. We welcome contributions of all OPs.
- **Period**. The size of metrics cache in minutes for checking the alarm conditions. This is a time window that corresponds to the backend deployment env time.
- **Count**. Within a period window, if the number of times which **value** goes over the threshold (based on OP) reaches `count`, then an alarm will be sent.
- **Only as condition**. Indicates if the rule can send notifications or if it simply serves as a condition of the composite rule.
- **Specific Hooks**. Binding the specific names of the hooks when the alarm is triggered.
  The name format is `{hookType}.{hookName}` (slackHooks.custom1 e.g.) and must be defined in the `hooks` section of the `alarm-settings.yml` file.
  If the hook name is not specified, the global hook will be used.
- **Silence period**. After the alarm is triggered at Time-N (TN), there will be silence during the **TN -> TN + period**.
By default, it works in the same manner as **period**. The same Alarm (having the same ID in the same metrics name) may only be triggered once within a period. 

Such as for a metric, there is a shifting window as following at T7.

| T1     | T2     | T3     | T4     | T5     | T6     | T7     |
|--------|--------|--------|--------|--------|--------|--------|
| Value1 | Value2 | Value3 | Value4 | Value5 | Value6 | Value7 |

* `Period`(Time point T1 ~ T7) are continuous data points for minutes. Notice, alerts are not supported above minute-by-minute periods as they would not be efficient.
* Values(Value1 ~ Value7) are the values or labeled values for every time point.
* `Count`'s value(N) represents there are N values in the window matched the operator and threshold.
* In every minute, the window would shift automatically. At T8, Value8 would be cached, and T1/Value1 would be removed from the window. 

### Composite rules
**NOTE**: Composite rules are only applicable to alerting rules targeting the same entity level, such as service-level alarm rules (`service_percent_rule && service_resp_time_percentile_rule`). Do not compose alarm rules of different entity levels, such as an alarm rule of the service metrics with another rule of the endpoint metrics.

A composite rule is made up of the following elements:
- **Rule name**. A unique name shown in the alarm message. Must end with `_rule`.
- **Expression**. Specifies how to compose rules, and supports `&&`, `||`, and `()`.
- **Message**. The notification message to be sent out when the rule is triggered.
- **Tags**. Tags are key/value pairs that are attached to alarms. Tags are used to specify distinguishing attributes of alarms that are meaningful and relevant to users.
- **Specific Hooks**. Binding the specific names of the hooks when the alarm is triggered.
  The name format is `{hookType}.{hookName}` (slackHooks.custom1 e.g.) and must be defined in the `hooks` section of the `alarm-settings.yml` file.
  If the hook name is not specified, the global hook will be used.

```yaml
rules:
  # Rule unique name, must be ended with `_rule`.
  endpoint_percent_rule:
    # Metrics value need to be long, double or int
    metrics-name: endpoint_percent
    threshold: 75
    op: <
    # The length of time to evaluate the metrics
    period: 10
    # How many times after the metrics match the condition, will trigger alarm
    count: 3
    # How many times of checks, the alarm keeps silence after alarm triggered, default as same as period.
    silence-period: 10
    # Specify if the rule can send notification or just as an condition of composite rule
    only-as-condition: false
    tags:
      level: WARNING
  service_percent_rule:
    metrics-name: service_percent
    # [Optional] Default, match all services in this metrics
    include-names:
      - service_a
      - service_b
    exclude-names:
      - service_c
    # Single value metrics threshold.
    threshold: 85
    op: <
    period: 10
    count: 4
    only-as-condition: false
  service_resp_time_percentile_rule:
    # Metrics value need to be long, double or int
    metrics-name: service_percentile
    op: ">"
    # Multiple value metrics threshold. Thresholds for P50, P75, P90, P95, P99.
    threshold: 1000,1000,1000,1000,1000
    period: 10
    count: 3
    silence-period: 5
    message: Percentile response time of service {name} alarm in 3 minutes of last 10 minutes, due to more than one condition of p50 > 1000, p75 > 1000, p90 > 1000, p95 > 1000, p99 > 1000
    only-as-condition: false
  meter_service_status_code_rule:
    metrics-name: meter_status_code
    exclude-labels:
      - "200"
    op: ">"
    threshold: 10
    period: 10
    count: 3
    silence-period: 5
    message: The request number of entity {name} non-200 status is more than expected.
    only-as-condition: false
    specific-hooks:
      - "slackHooks.custom1"
      - "pagerDutyHooks.custom1"
composite-rules:
  comp_rule:
    # Must satisfied percent rule and resp time rule 
    expression: service_percent_rule && service_resp_time_percentile_rule
    message: Service {name} successful rate is less than 80% and P50 of response time is over 1000ms
    tags:
      level: CRITICAL
    specific-hooks:
      - "slackHooks.default"
      - "slackHooks.custom1"
      - "pagerDutyHooks.custom1"
```


### Default alarm rules
For convenience's sake, we have provided a default `alarm-setting.yml` in our release. It includes the following rules:
1. Service average response time over 1s in the last 3 minutes.
1. Service success rate lower than 80% in the last 2 minutes.
1. Percentile of service response time over 1s in the last 3 minutes
1. Service Instance average response time over 1s in the last 2 minutes, and the instance name matches the regex.
1. Endpoint average response time over 1s in the last 2 minutes.
1. Database access average response time over 1s in the last 2 minutes.
1. Endpoint relation average response time over 1s in the last 2 minutes.

### List of all potential metrics name
The metrics names are defined in the official [OAL scripts](../../guides/backend-oal-scripts.md) and
[MAL scripts](../../concepts-and-designs/mal.md), the [Event](../../concepts-and-designs/event.md) names can also serve
as the metrics names, all possible event names can be also found in [the Event doc](../../concepts-and-designs/event.md).

Currently, metrics from the **Service**, **Service Instance**, **Endpoint**, **Service Relation**, **Service Instance Relation**, **Endpoint Relation** scopes could be used in Alarm, and the **Database access** scope is the same as **Service**.

Submit an issue or a pull request if you want to support any other scopes in Alarm.

## Hooks
Hooks are a way to send alarm messages to the outside world. SkyWalking supports multiple hooks of the same type, each hook can support different configurations. 
For example, you can configure two Slack hooks, one named `default` and set `isGlobal: true` means this hook will apply on all `Alarm Rules` **without config** `specific-hooks`.
Another named `custom1` will only apply on the `Alarm Rules` which **with config** `specific-hooks` and include the name `slackHooks.custom1`.

```yaml
hooks:
  slackHooks:
    default:
      isGlobal: true # if true, this hook will apply on all rules, unless a rule has its own specific hook.
      textTemplate: |-
        {
          "type": "section",
          "text": {
            "type": "mrkdwn",
            "text": ":alarm_clock: *Apache Skywalking Alarm* \n **%s**."
          }
        }
      webhooks:
        - https://hooks.slack.com/services/x/y/zssss
    custom1:
      textTemplate: |-
        {
          "type": "section",
          "text": {
            "type": "mrkdwn",
            "text": ":alarm_clock: *Apache Skywalking Alarm* \n **%s**."
          }
        }
      webhooks:
        - https://hooks.slack.com/services/x/y/custom1
```

Currently, SkyWalking supports the following hook types:

### Webhook
The Webhook requires the peer to be a web container. The alarm message will be sent through HTTP post by `application/json` content type. The JSON format is based on `List<org.apache.skywalking.oap.server.core.alarm.AlarmMessage>` with the following key information:
- **scopeId**, **scope**. All scopes are defined in `org.apache.skywalking.oap.server.core.source.DefaultScopeDefine`.
- **name**. Target scope entity name. Please follow the [entity name definitions](#entity-name).
- **id0**. The ID of the scope entity that matches with the name. When using the relation scope, it is the source entity ID.
- **id1**. When using the relation scope, it is the destination entity ID. Otherwise, it is empty.
- **ruleName**. The rule name configured in `alarm-settings.yml`.
- **alarmMessage**. The alarm text message.
- **startTime**. The alarm time measured in milliseconds, which occurs between the current time and the midnight of January 1, 1970 UTC.
- **tags**. The tags configured in `alarm-settings.yml`.

See the following example:
```json
[{
  "scopeId": 1, 
  "scope": "SERVICE",
  "name": "serviceA", 
  "id0": "12",  
  "id1": "",  
    "ruleName": "service_resp_time_rule",
  "alarmMessage": "alarmMessage xxxx",
  "startTime": 1560524171000,
    "tags": [{
        "key": "level",
        "value": "WARNING"
     }]
}, {
  "scopeId": 1,
  "scope": "SERVICE",
  "name": "serviceB",
  "id0": "23",
  "id1": "",
    "ruleName": "service_resp_time_rule",
  "alarmMessage": "alarmMessage yyy",
  "startTime": 1560524171000,
    "tags": [{
        "key": "level",
        "value": "CRITICAL"
    }]
}]
```

### gRPCHooks
The alarm message will be sent through remote gRPC method by `Protobuf` content type. 
The message contains key information which are defined in `oap-server/server-alarm-plugin/src/main/proto/alarm-hook.proto`.

Part of the protocol looks like this:
```protobuf
message AlarmMessage {
    int64 scopeId = 1;
    string scope = 2;
    string name = 3;
    string id0 = 4;
    string id1 = 5;
    string ruleName = 6;
    string alarmMessage = 7;
    int64 startTime = 8;
    AlarmTags tags = 9;
}

message AlarmTags {
    // String key, String value pair.
    repeated KeyStringValuePair data = 1;
}

message KeyStringValuePair {
    string key = 1;
    string value = 2;
}
```

### Slack Chat Hook
Follow the [Getting Started with Incoming Webhooks guide](https://api.slack.com/messaging/webhooks) and create new Webhooks.

The alarm message will be sent through HTTP post by `application/json` content type if you have configured Slack Incoming Webhooks as follows:
```yml
slackHooks:
  default:
    isGlobal: true
    textTemplate: |-
      {
        "type": "section",
        "text": {
          "type": "mrkdwn",
          "text": ":alarm_clock: *Apache Skywalking Alarm* \n **%s**."
        }
      }
    webhooks:
    - https://hooks.slack.com/services/x/y/z
```

### WeChat Hook
Note that only the WeChat Company Edition (WeCom) supports WebHooks. To use the WeChat WebHook, follow the [Wechat Webhooks guide](https://work.weixin.qq.com/help?doc_id=13376).
The alarm message will be sent through HTTP post by `application/json` content type after you have set up Wechat Webhooks as follows:
```yml
wechatHooks:
  default:
    isGlobal: true
    textTemplate: |-
      {
        "msgtype": "text",
        "text": {
          "content": "Apache SkyWalking Alarm: \n %s."
        }
      }
    webhooks:
    - https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=dummy_key
```

### DingTalk Hook
Follow the [Dingtalk Webhooks guide](https://ding-doc.dingtalk.com/doc#/serverapi2/qf2nxq/uKPlK) and create new Webhooks.
You can configure an optional secret for an individual webhook URL for security purposes.
The alarm message will be sent through HTTP post by `application/json` content type if you have configured DingTalk Webhooks as follows:
```yml
dingtalkHooks:
  default:
    isGlobal: true
    textTemplate: |-
      {
        "msgtype": "text",
        "text": {
          "content": "Apache SkyWalking Alarm: \n %s."
        }
      }
    webhooks:
    - url: https://oapi.dingtalk.com/robot/send?access_token=dummy_token
      secret: dummysecret
```

### Feishu Hook
Follow the [Feishu Webhooks guide](https://www.feishu.cn/hc/zh-cn/articles/360024984973) and create new Webhooks.
You can configure an optional secret for an individual webhook URL for security purposes.
If you want to direct a text to a user, you can configure `ats`, which is Feishu's user_id and separated by "," .
The alarm message will be sent through HTTP post by `application/json` content type if you have configured Feishu Webhooks as follows:
```yml
feishuHooks:
  default:
    isGlobal: true
    textTemplate: |-
    {
      "msg_type": "text",
      "content": {
        "text": "Apache SkyWalking Alarm: \n %s."
      },
      "ats":"feishu_user_id_1,feishu_user_id_2"
    }
    webhooks:
    - url: https://open.feishu.cn/open-apis/bot/v2/hook/dummy_token
      secret: dummysecret
```

### WeLink Hook
Follow the [WeLink Webhooks guide](https://open.welink.huaweicloud.com/apiexplorer/#/apiexplorer?type=internal&method=POST&path=/welinkim/v1/im-service/chat/group-chat) and create new Webhooks.
The alarm message will be sent through HTTP post by `application/json` content type if you have configured WeLink Webhooks as follows:
```yml
welinkHooks:
  default:
    isGlobal: true
    textTemplate: "Apache SkyWalking Alarm: \n %s."
    webhooks:
    # you may find your own client_id and client_secret in your app, below are dummy, need to change.
    - client_id: "dummy_client_id"
      client_secret: dummy_secret_key
      access_token_url: https://open.welink.huaweicloud.com/api/auth/v2/tickets
      message_url: https://open.welink.huaweicloud.com/api/welinkim/v1/im-service/chat/group-chat
      # if you send to multi group at a time, separate group_ids with commas, e.g. "123xx","456xx"
      group_ids: "dummy_group_id"
      # make a name you like for the robot, it will display in group
      robot_name: robot
```


### PagerDuty Hook
The PagerDuty hook is based on [Events API v2](https://developer.pagerduty.com/docs/ZG9jOjExMDI5NTgw-events-api-v2-overview).

Follow the [Getting Started](https://developer.pagerduty.com/docs/ZG9jOjExMDI5NTgw-events-api-v2-overview#getting-started) section to create an **Events API v2** integration on your PagerDuty service and copy the integration key.

Then configure as follows:
```yml
pagerDutyHooks:
  default:
    isGlobal: true
    textTemplate: "Apache SkyWalking Alarm: \n %s."
    integrationKeys:
    - 5c6d805c9dcf4e03d09dfa81e8789ba1
```

You can also configure multiple integration keys.

### Discord Hook
Follow the [Discord Webhooks guide](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks) and create a new webhook.

Then configure as follows:
```yml
discordHooks:
  default:
    isGlobal: true
    textTemplate: "Apache SkyWalking Alarm: \n %s."
    webhooks:
    - url: https://discordapp.com/api/webhooks/1008166889777414645/8e0Am4Zb-YGbBqqbiiq0jSHPTEEaHa4j1vIC-zSSm231T8ewGxgY0_XUYpY-k1nN4HBl
      username: robot
```

## Update the settings dynamically
Since 6.5.0, the alerting settings can be updated dynamically at runtime by [Dynamic Configuration](dynamic-config.md),
which will override the settings in `alarm-settings.yml`.

In order to determine whether an alerting rule is triggered or not, SkyWalking needs to cache the metrics of a time window for
each alerting rule. If any attribute (`metrics-name`, `op`, `threshold`, `period`, `count`, etc.) of a rule is changed,
the sliding window will be destroyed and re-created, causing the Alarm of this specific rule to restart again.

### Keys with data types of alerting rule configuration file

| Alerting element     | Configuration property key | Type           | Description        |
|----------------------|----------------------------|----------------|--------------------|
| Include names        | include-names              | string array   |                    | 
| Exclude names        | exclude-names              | string array   |                    | 
| Include names regex  | include-names-regex        | string         | Java regex Pattern |
| Exclude names regex  | exclude-names-regex        | string         | Java regex Pattern |
| Include labels       | include-labels             | string array   |                    |
| Exclude labels       | exclude-labels             | string array   |                    |
| Include labels regex | include-labels-regex       | string         | Java regex Pattern |
| Exclude labels regex | exclude-labels-regex       | string         | Java regex Pattern |
| Tags                 | tags                       | key-value pair |                    |
| Threshold            | threshold                  | number         |                    |
| OP                   | op                         | operator       | example: `>`, `>=` |
| Period               | Period                     | int            |                    |
| Count                | count                      | int            |                    |
| Only as condition    | only-as-condition          | boolean        |                    |
| Silence period       | silence-period             | int            |                    |
| Message              | message                    | string         |                    |
| Specific Hooks       | specific-hooks             | string array   |                    |

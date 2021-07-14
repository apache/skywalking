# 告警

告警的内核是被一系列规则所驱动的，这些规则定义在`config/alarm-setting.yml`中。 告警规则的定义包含3部分。

1. [告警规则](#规则)。定义了要告警的指标阈值以及告警需要满足的条件
1. [网络回调](#Webhook/网络回调)。告警触发时需要回调的网络服务列表
1. [gRPC回调](#gRPCHook/gRPC回调)。告警触发时需要调用的gRPC服务主机、端口和方法

## 实体名称

定义作用域和实体名称的关系

- **Service**: 服务名称
- **Instance**: {服务}的{实例名称}
- **Endpoint**: {服务}的{终端名称}
- **Database**: 数据库服务名称
- **Service Relation**: {源服务}到{目标服务}
- **Instance Relation**: {源服务}的{源实例}到{目标服务}的{目标实例}
- **Endpoint Relation**: {源服务}的{终端}到{目标服务}的{终端}

## 规则

**存在两种类型的规则：独立规则和复合规则。复合规则是独立规则的结合体。**

### 独立规则

一条告警规则由以下元素组成：

- **Rule name规则名称**。告警信息中展示的唯一名称。必须以`_rule`结尾。
- **Metrics name指标名称**。这也是OAL脚本中的指标名称。只支持long、double、int类型。请查看[潜在的内置指标名称列表](#list-of-all-potential-metrics-name)
  。事件也可以被配置成告警的源，请查阅[事件文档](../../concepts-and-designs/event.md)获取更多详情。
- **Include names包含的名称**。该条规则包含的实体名称。请根据[实体名称定义](#实体名称)进行配置。
- **Exclude names不包含的名称**。该条规则不包含的实体名称。请根据[实体名称定义](#实体名称)进行配置。
- **Include names regex包含的名称正则**。该条规则需要包含的实体名称正则表达式。如果include-name和include-name regex都设置了，它们都会生效。
- **Exclude names regex不包含的名称正则**。该条规则不包含的实体名称正则表达式。如果exclude-name和exclude-name regex都设置了，它们都会生效。
- **Include labels包含的标签**。该条规则包含的指标的标签。
- **Exclude labels不包含的标签**。该条规则不应包含的指标的标签
- **Include labels regex包含的标签正则**。需要包含的标签的正则表达式。如果include-label和include-label regex都设置了，那么它们都会生效。
- **Exclude labels regex不包含的标签正则**。不应包含的标签的正则表达式。如果exclude-label和exclude-label regex都设置了，那么它们都会生效。
- **标记**
  标记是一组附加给告警的键值对。标记是用于区分出对用户有意义且相关的告警的属性。如果你想让这些属标记在Skywalking的UI后台上能给搜索的话，你需要将这些标记的key配置到`core/default/searchableAlarmTags`
  中，也可以通过系统环境变量`SW_SEARCHABLE_ALARM_TAG_KEYS`进行配置。其中的`level`是默认添加的。

*在计量系统中，标签的设置是必须的。它们用于在指标-系统平台存储指标信息，像Prometheus、MicroMeter等。上面提到的四种标签设置都需要实现`LabeledValueHolder`。*

- **Threshold阈值**。目标值。对于多值的指标，像**百分比**，阈值是一个数组。值可以描述成：`value1, value2, value3, value4, value5`。每个值可以用于指标值的不同阈值。
  如果不想告警被一个或多个值触发，你也可以将值设置为`-`。比如**百分比**，`value1`代表P50的阈值，`-, -, value3, value4, value5`代表P50和P75是没有阈值的。

- **OP**。操作符。支持`>`，`>=`，`<`，`<=`，`=`。我们欢迎对所有操作符的支持的代码贡献。
- **Period**。告警规则的检查频率。这是一个对应于后台系统部署环境时间的时间窗口。
- **Count**。在时间窗口内，如果**值**超过阈值的次数达到了`count`，那么告警将会被发送。
- **Only as condition**。表明此规则是否能发送通知，或者只是作为复合规则的一部分而已。

- **Silence period**。当告警在Time-N的时候被触发，那么在**TN -> TN + period**这段时间告警是静默的。默认情况下， 它的工作方式和与**period**
  相同。同样的告警（同样的指标中拥有同样的一个ID）在一个时间窗口内只能被触发一次。

### 复合规则

**注意**：复合规则仅适用于作用在同一实体级别的规则，例如服务-级别的告警规则（`service_percent_rule && service_resp_time_percentile_rule`）。
不要将不同实体级别的规则组合在一起，例如一个告警规则是服务级别的，而另一个是终端级别上的。

一条复合规则由以下元素组成：

- **Rule name规则名称**：告警信息中展示的唯一名称。必须以`_rule`结尾。
- **Expression表达式**。指明怎样组合规则，仅支持`&&`，`||`，`()`。
- **Message消息**。告警被触发时需要发送的通知消息。
- **Tags**。标记是一组附加给告警的键值对。标记是用于区分出对用户有意义且相关的告警的属性。

```yaml
rules:
  # 规则名称 以`_rule`.
  endpoint_percent_rule:
    # 指标阈值只能是long, double or int
    metrics-name: endpoint_percent
    threshold: 75
    op: <
    # 计算指标的时间窗口大小
    period: 10
    # 几次达到阈值后，触发告警
    count: 3
    # 告警触发后，保持多久的静默；默认和period一样
    silence-period: 10
    # 知名该规则是否可以发送通知，或者只是作为复合规则的一部分而已
    only-as-condition: false
    tags:
      level: WARNING
  service_percent_rule:
    metrics-name: service_percent
    # [可选] 默认，匹配所有在此指标中的service
    include-names:
      - service_a
      - service_b
    exclude-names:
      - service_c
    # 单值的指标阈值
    threshold: 85
    op: <
    period: 10
    count: 4
    only-as-condition: false
  service_resp_time_percentile_rule:
    # 指标值需要为long,double,int
    metrics-name: service_percentile
    op: ">"
    # 多值的指标阈值。for：P50, P75, P90, P95, P99.
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
composite-rules:
  comp_rule:
    # 需要同事满足percent rule和resp time rule
    expression: service_percent_rule && service_resp_time_percentile_rule
    message: Service {name} successful rate is less than 80% and P50 of response time is over 1000ms
    tags:
      level: CRITICAL
```

### 默认告警规则

为了方便起见，在发布版本中，我们提供了一个默认的`alarm-setting.yml`。它包含了下面的规则：

1. 在最近3分钟内，服务平均响应时间超过1s。
1. 在最近2分钟内，服务成功率低于80%。
1. 在最近3分钟内，服务响应时间超过1s的百分比
1. 在最近2分钟内，匹配正则的实例的平均响应时间超过1s
1. 在最近2分钟内，终端的平均响应时间超过1s
1. 在最近2分钟内，数据库访问平均响应时间超过1s
1. 在最近2分钟内，终端调用平均响应时间超过1s

### 内置的指标名称列表

指标名称又官方定义[OAL脚本](../../guides/backend-oal-scripts.md)，[MAL脚本](../../concepts-and-designs/mal.md)，
[事件](../../concepts-and-designs/event.md)也可以作为指标的名称，所有可能的事件名称可以在[事件文档](../../concepts-and-designs/event.md)中找到。

目前，Alarm 中可以使用服务、服务实例、终端、服务关联、服务实例关联、终端关联范围的指标，Database的访问范围与Service是相同的。

如果您想支持任何其他警报范围，请提交问题或PR

## Webhook/网络回调

网络回调需要web容器的一个入口。告警的消息将以`application/json`格式通过HTTP
post进行发送。JSON消息的格式是基于`List<org.apache.skywalking.oap.server.core.alarm.AlarmMessage>`的，主要包含的信息如下：

- **scopeId范围Id**，**scope范围**。所以的范围定义在`org.apache.skywalking.oap.server.core.source.DefaultScopeDefine`。 The Webhook
- **name名称**。目标范围实体名称。请参照[实体名称定义](#实体名称)。
- **id0**。匹配中名称的范围实体的ID。当使用的是关联范围，它是源实体的ID。

- **id1**。当使用的是关联范围，它是目标实体的ID。否则，它为空。
- **ruleName规则名称**。 在`alarm-settings.yml`中配置的规则名称。
- **alarmMessage**。告警的文本信息内容。
- **startTime**。 以毫秒计量的告警时间。在1970年1月1日到当前时间之间。
- **标记**。在`alarm-settings.yml`配置的标记

看下面的例子:

```json
[
  {
    "scopeId": 1,
    "scope": "SERVICE",
    "name": "serviceA",
    "id0": "12",
    "id1": "",
    "ruleName": "service_resp_time_rule",
    "alarmMessage": "alarmMessage xxxx",
    "startTime": 1560524171000,
    "tags": [
      {
        "key": "level",
        "value": "WARNING"
      }
    ]
  },
  {
    "scopeId": 1,
    "scope": "SERVICE",
    "name": "serviceB",
    "id0": "23",
    "id1": "",
    "ruleName": "service_resp_time_rule",
    "alarmMessage": "alarmMessage yyy",
    "startTime": 1560524171000,
    "tags": [
      {
        "key": "level",
        "value": "CRITICAL"
      }
    ]
  }
]
```

## gRPCHook/gRPC回调

告警信息将以`Protobuf`的格式通过gRPC进行发送。消息包含的主要内容定义在`oap-server/server-alarm-plugin/src/main/proto/alarm-hook.proto`。

部分协议如下所示：

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

## Slack Chat Hook / Slack Chat 回调

根据[WebHooks指南](https://api.slack.com/messaging/webhooks)来创建新的回调。

当你配置了如下的WebHooks，告警的消息将以`application/json`格式通过HTTP post进行发送。

```yml
slackHooks:
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

## WeChat Hook / 微信回调

注意，只有weChat版本才支持此WebHook。想要使用微信Webhook，请参照[微信回调指南](https://work.weixin.qq.com/help?doc_id=13376)。
在完成如下微信Webhook配置后，告警的消息将以`application/json`格式通过HTTP post进行发送。

```yml
wechatHooks:
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

## Dingtalk Hook / 钉钉回调

参照[钉钉Webhook指南](https://ding-doc.dingtalk.com/doc#/serverapi2/qf2nxq/uKPlK)创建新的Webhook。出于安全考虑，你可以为单独的webhook
URL地址配置可选的秘钥。当你配置了如下的钉钉Webhook，告警的消息将以`application/json`格式通过HTTP post进行发送。

```yml
dingtalkHooks:
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

## Feishu Hook / 飞书回调

参照[飞书回调指南](https://www.feishu.cn/hc/zh-cn/articles/360024984973)创建新的Webhook。出于安全考虑，你可以为单独的webhook
URL地址配置可选的秘钥。如果你想直接发送文本一个用户，你可以用逗号分隔的user_id来配置`ats`来实现。当你配置了如下的飞书Webhook，告警的消息将以`application/json`格式通过HTTP post进行发送。

```yml
feishuHooks:
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

## WeLink Hook / 华为WeLink回调

参照[WeLink回调指南](https://open.welink.huaweicloud.com/apiexplorer/#/apiexplorer?type=internal&method=POST&path=/welinkim/v1/im-service/chat/group-chat)创建新的Webhook。
当你配置了如下的WeLink Webhook，告警的消息将以`application/json`格式通过HTTP post进行发送。

```yml
welinkHooks:
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

## 动态更新配置

从6.5.0开始，告警设置可以在运行时通过[动态配置](dynamic-config.md)动态更新，它将覆盖`alarm-settings.yml`中的配置。

SkyWalking为了判断一条报警规则是否被触发，需要缓存每条报警规则的时间窗口指标。如果规则的任何属性（`规则名称`、`操作符`、`阈值`、`时间窗口`、`累计次数`
等）发生变化，滑动窗口将被销毁并重新创建，从而会导致这个特定的规则重新启动。


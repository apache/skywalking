## 观测分析语言
提供OAL(可观测性分析语言)来分析流模式下传入的数据。

OAL主要关注服务， 服务实例和端点的度量、指标信息。正因为如此, OAL很容易学习和使用。

考虑到性能、读取和调试, OAL被定义为一种编译语言。
OAL脚本将在打包阶段编译为普通的java代码。

## 语法
脚本必须以`*.oal`命名结尾。

```

METRIC_NAME = from(SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])
```

## 作用域（Scope）
主要的 **SCOPE** 包含 are `All`, `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`.
其中有一些二级作用域，隶属于一级作用域。

你可以在[Scope定义](scope-definitions-cn.md)中查看到所有的作用域（Scope）和字段（Fields）。

## 过滤器
通过使用字段名称和表达式的过滤器来生成字段值的条件。

表达式支持通过`and`，`or`和 `(...)`进行链接。
操作运算符支持`=`, `!=`, `>`, `<`, `in (v1, v2, ...`, `like "%..."`。在语法不支持或错误的情况下进行触发器编译
或代码生成的时候，基于字段类型的类型检测会报错。

## 聚合函数
SkyWalking OAP core提供了一些默认的函数，你也可以实现更多的聚合函数。

提供的函数
- `longAvg`.来源数据的平均值。输入的字段必须是long类型的。
> instance_jvm_memory_max = from(ServiceInstanceJVMMemory.max).longAvg();

此例中, 输入数据是每个ServiceInstanceJVMMemory的请求, 计算基于字段`max`的平均值。

- `doubleAvg`.来源数据的平均值。输入的字段必须是double类型的。
> instance_jvm_cpu = from(ServiceInstanceJVMCPU.usePercent).doubleAvg();

此例中, 输入是每个ServiceInstanceJVMCPU的请求，计算基于字段`usePercent`的平均值。

- `percent`. 统计来源数据中符合条件的百分比。
> endpoint_percent = from(Endpoint.*).percent(status == true);

此例中, 所有输入都是每个端点的请求，匹配条件是`endpoint.status == true`。

- `sum`.统计来源数据中的总和。
> Service_Calls_Sum = from(Service.*).sum();

此例中，代表着统计每个服务的调用次数。

- `p99`, `p95`, `p90`, `p75`, `p50`. 参考[p99 in WIKI](https://en.wikipedia.org/wiki/Percentile)
> All_p99 = from(All.latency).p99(10);

此例中，统计来源数据的百分之九十九的情况。

- `thermodynamic`.参考[Headmap in WIKI](https://en.wikipedia.org/wiki/Heat_map))
> All_heatmap = from(All.latency).thermodynamic(100, 20);

此例中，统计所有来源数据的热力图。

## 指标、度量名称
存储实现器、警报和查询模块的指标名称。类型推断由skywalking core支持。

## 分组
所有指标数据将Scope.ID和最小量级的时间桶分组。

- 在`Endpoint` scope中, Scope.ID = Endpoint id (基于服务及其端点的唯一id)

## 更多例子
```
// Caculate p99 of both Endpoint1 and Endpoint2
Endpoint_p99 = from(Endpoint.latency).filter(name in ("Endpoint1", "Endpoint2")).summary(0.99)

// Caculate p99 of Endpoint name started with `serv`
serv_Endpoint_p99 = from(Endpoint.latency).filter(name like ("serv%")).summary(0.99)

// Caculate the avg response time of each Endpoint
Endpoint_avg = from(Endpoint.latency).avg()

// Caculate the histogram of each Endpoint by 50 ms steps.
// Always thermodynamic diagram in UI matches this metric. 
Endpoint_histogram = from(Endpoint.latency).histogram(50)

// Caculate the percent of response status is true, for each service.
Endpoint_success = from(Endpoint.*).filter(status = "true").percent()

// Caculate the percent of response code in [200, 299], for each service.
Endpoint_200 = from(Endpoint.*).filter(responseCode like "2%").percent()

// Caculate the percent of response code in [500, 599], for each service.
Endpoint_500 = from(Endpoint.*).filter(responseCode like "5%").percent()

// Caculate the sum of calls for each service.
EndpointCalls = from(Endpoint.*).sum()
```

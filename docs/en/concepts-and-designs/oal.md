# Analysis Native Streaming Traces and Service Mesh Traffic

The traces in SkyWalking native format and Service Mesh Traffic(Access Log in gRPC) are able to be analyzed by OAL,
to build metrics of services, service instances and endpoints, and to build topology/dependency of services, service 
instances and endpoints(traces-oriented analysis only).

The spans of traces relative with RPC, such as HTTP, gRPC, Dubbo, RocketMQ, Kafka, would be converted to service input/output 
traffic, like access logs collected from service mesh. Both of those traffic would be cataloged as the defined sources
in the `Observability Analysis Language` engine.

The metrics are customizable through Observability Analysis Language(OAL) scripts, 
and the topology/dependency is built by the SkyWalking OAP kernel automatically without
explicit OAL scripts.

# Observability Analysis Language
OAL(Observability Analysis Language) serves to analyze incoming data in streaming mode. 

OAL focuses on metrics in Service, Service Instance and Endpoint. Therefore, the language is easy to 
learn and use.

OAL scripts are now found in the `/config` folder, and users could simply change and reboot the server to run them.
However, the OAL script is a compiled language, and the OAL Runtime generates java codes dynamically. Don't expect to mount
the changes of those scripts in the runtime.
If your OAP servers are running in a cluster mode, these script defined metrics should be aligned.

You can open set `SW_OAL_ENGINE_DEBUG=Y` at system env to see which classes are generated.

## Grammar
Scripts should be named `*.oal`
```
// Declare the metrics.
METRICS_NAME = from(CAST SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(CAST FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])

// Disable hard code 
disable(METRICS_NAME);
```

## From
The **from** statement defines the data source of this OAL expression.

Primary **SCOPE**s are `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, and `EndpointRelation`.
There are also some secondary scopes which belong to a primary scope. 

See [Scope Definitions](scope-definitions.md), where you can find all existing Scopes and Fields.


## Filter
Use filter to build conditions for the value of fields by using field name and expression. 

The filter expressions run as a chain, generally connected with `logic AND`. 
The OPs support `==`, `!=`, `>`, `<`, `>=`, `<=`, `in [...]` ,`like %...`, `like ...%` , `like %...%` , `contain` and `not contain`, with type detection based on field type. In the event of incompatibility, compile or code generation errors may be triggered. 

## Aggregation Function
The default functions are provided by the SkyWalking OAP core, and it is possible to implement additional functions. 

Functions provided
- `longAvg`. The avg of all input per scope entity. The input field must be a long.
> instance_jvm_memory_max = from(ServiceInstanceJVMMemory.max).longAvg();

In this case, the input represents the request of each ServiceInstanceJVMMemory scope, and avg is based on field `max`.
- `doubleAvg`. The avg of all input per scope entity. The input field must be a double.
> instance_jvm_cpu = from(ServiceInstanceJVMCPU.usePercent).doubleAvg();

In this case, the input represents the request of each ServiceInstanceJVMCPU scope, and avg is based on field `usePercent`.
- `percent`. The number or ratio is expressed as a fraction of 100, where the input matches with the condition.
> endpoint_percent = from(Endpoint.*).percent(status == true);

In this case, all input represents requests of each endpoint, and the condition is `endpoint.status == true`.
- `rate`. The rate expressed is as a fraction of 100, where the input matches with the condition.
> browser_app_error_rate = from(BrowserAppTraffic.*).rate(trafficCategory == BrowserAppTrafficCategory.FIRST_ERROR, trafficCategory == BrowserAppTrafficCategory.NORMAL);

In this case, all input represents requests of each browser app traffic, the `numerator` condition is `trafficCategory == BrowserAppTrafficCategory.FIRST_ERROR` and `denominator` condition is `trafficCategory == BrowserAppTrafficCategory.NORMAL`.
Parameter (1) is the `numerator` condition.
Parameter (2) is the `denominator` condition.
- `count`. The sum of calls per scope entity.
> service_calls_sum = from(Service.*).count();

In this case, the number of calls of each service. 

- `histogram`. See [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map).
> service_heatmap = from(Service.latency).histogram(100, 20);

In this case, the thermodynamic heatmap of all incoming requests. 
Parameter (1) is the precision of latency calculation, such as in the above case, where 113ms and 193ms are considered the same in the 101-200ms group.
Parameter (2) is the group amount. In the above case, 21(param value + 1) groups are 0-100ms, 101-200ms, ... 1901-2000ms, 2000+ms 

- `apdex`. See [Apdex in WIKI](https://en.wikipedia.org/wiki/Apdex).
> service_apdex = from(Service.latency).apdex(name, status);

In this case, the apdex score of each service.
Parameter (1) is the service name, which reflects the Apdex threshold value loaded from service-apdex-threshold.yml in the config folder.
Parameter (2) is the status of this request. The status(success/failure) reflects the Apdex calculation.

- `p99`, `p95`, `p90`, `p75`, `p50`. See [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile).
> service_percentile = from(Service.latency).percentile2(10);

**percentile (deprecated since 10.0.0)** is the first multiple-value metric, which has been introduced since 7.0.0. As a metric with multiple values, it could be queried through the `getMultipleLinearIntValues` GraphQL query.
**percentile2** Since 10.0.0, the `percentile` function has been instead by `percentile2`. The `percentile2` function is a labeled-value metric with default label name `p` and label values `50`,`75`,`90`,`95`,`99`.
In this case, see `p99`, `p95`, `p90`, `p75`, and `p50` of all incoming requests. The parameter is precise to a latency at p99, such as in the above case, and 120ms and 124ms are considered to produce the same response time.

In this case, the p99 value of all incoming requests. The parameter is precise to a latency at p99, such as in the above case, and 120ms and 124ms are considered to produce the same response time.

- `labelCount`. The count of the label value.
> drop_reason_count = from(CiliumService.*).filter(verdict == "dropped").labelCount(dropReason, 100);

In this case, the count of the drop reason of each Cilium service, max support calculate `100` reasons(optional configuration). 

- `labelAvg`. The avg of the label value.
> drop_reason_avg = from(BrowserResourcePerf.*).labelAvg(name, duration, 100);

In this case, the avg of the duration of each browser resource file, max support calculate `100` resource file(optional configuration).

## Metrics name
The metrics name for storage implementor, alarm and query modules. The type inference is supported by core.

## Group
All metrics data will be grouped by Scope.ID and min-level TimeBucket. 

- In the `Endpoint` scope, the Scope.ID is same as the Endpoint ID (i.e. the unique ID based on service and its endpoint).

## Cast
Fields of source are static type. In some cases, the type required by the filter expression and aggregation function doesn't 
match the type in the source, such as tag value in the source is String type, most aggregation calculation requires numeric.

Cast expression is provided to do so. 
- `(str->long)` or `(long)`, cast string type into long.
- `(str->int)` or `(int)`, cast string type into int.

```
mq_consume_latency = from((str->long)Service.tag["transmission.latency"]).longAvg(); // the value of tag is string type.
```

Cast statement is supported in
1. **From statement**. `from((cast)source.attre)`. 
2. **Filter expression**. `.filter((cast)tag["transmission.latency"] > 0)`
3. **Aggregation function parameter**. `.longAvg((cast)strField1== 1,  (cast)strField2)`

## Decorator
`decorator` is to select a specific decorator to decorate the source for the metrics.
> service_resp_time = from(Service.latency).longAvg().decorator("ServiceDecorator");

In this case, the `ServiceDecorator` is the `Java Class simple name` which used to decorate the source of `Service` before the aggregation function.
This function is used to add additional attributes to the metrics. More details, see [Metrics Additional Attributes](metrics-additional-attributes.md).

## Disable
`Disable` is an advanced statement in OAL, which is only used in certain cases.
Some of the aggregation and metrics are defined through core hard codes. Examples include `segment` and `top_n_database_statement`.
This `disable` statement is designed to render them inactive.
By default, none of them are disabled.

**NOTICE**, all disable statements should be in `oal/disable.oal` script file. 

## Examples
```
// Calculate p99 of both Endpoint1 and Endpoint2
endpoint_p99 = from(Endpoint.latency).filter(name in ("Endpoint1", "Endpoint2")).summary(0.99)

// Calculate p99 of Endpoint name started with `serv`
serv_Endpoint_p99 = from(Endpoint.latency).filter(name like "serv%").summary(0.99)

// Calculate the avg response time of each Endpoint
endpoint_resp_time = from(Endpoint.latency).avg()

// Calculate the p50, p75, p90, p95 and p99 of each Endpoint by 50 ms steps.
endpoint_percentile = from(Endpoint.latency).percentile2(10)

// Calculate the percent of response status is true, for each service.
endpoint_success = from(Endpoint.*).filter(status == true).percent()

// Calculate the sum of response code in [404, 500, 503], for each service.
endpoint_abnormal = from(Endpoint.*).filter(httpResponseStatusCode in [404, 500, 503]).count()

// Calculate the sum of request type in [RequestType.RPC, RequestType.gRPC], for each service.
endpoint_rpc_calls_sum = from(Endpoint.*).filter(type in [RequestType.RPC, RequestType.gRPC]).count()

// Calculate the sum of endpoint name in ["/v1", "/v2"], for each service.
endpoint_url_sum = from(Endpoint.*).filter(name in ["/v1", "/v2"]).count()

// Calculate the sum of calls for each service.
endpoint_calls = from(Endpoint.*).count()

// Calculate the CPM with the GET method for each service.The value is made up with `tagKey:tagValue`.
// Option 1, use `tags contain`.
service_cpm_http_get = from(Service.*).filter(tags contain "http.method:GET").cpm()
// Option 2, use `tag[key]`.
service_cpm_http_get = from(Service.*).filter(tag["http.method"] == "GET").cpm();

// Calculate the CPM with the HTTP method except for the GET method for each service.The value is made up with `tagKey:tagValue`.
service_cpm_http_other = from(Service.*).filter(tags not contain "http.method:GET").cpm()

disable(segment);
disable(endpoint_relation_server_side);
disable(top_n_database_statement);
```

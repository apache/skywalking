# Observability Analysis Language
OAL(Observability Analysis Language) serves to analyze incoming data in streaming mode. 

OAL focuses on metrics in Service, Service Instance and Endpoint. Therefore, the language is easy to 
learn and use.


Since 6.3, the OAL engine is embedded in OAP server runtime as `oal-rt`(OAL Runtime).
OAL scripts are now found in the `/config` folder, and users could simply change and reboot the server to run them.
However, the OAL script is a compiled language, and the OAL Runtime generates java codes dynamically.

You can open set `SW_OAL_ENGINE_DEBUG=Y` at system env to see which classes are generated.

## Grammar
Scripts should be named `*.oal`
```
// Declare the metrics.
METRICS_NAME = from(SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])

// Disable hard code 
disable(METRICS_NAME);
```

## Scope
Primary **SCOPE**s are `All`, `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, and `EndpointRelation`.
There are also some secondary scopes which belong to a primary scope. 

See [Scope Definitions](scope-definitions.md), where you can find all existing Scopes and Fields.


## Filter
Use filter to build conditions for the value of fields by using field name and expression. 

The expressions support linking by `and`, `or` and `(...)`. 
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
> all_heatmap = from(All.latency).histogram(100, 20);

In this case, the thermodynamic heatmap of all incoming requests. 
Parameter (1) is the precision of latency calculation, such as in the above case, where 113ms and 193ms are considered the same in the 101-200ms group.
Parameter (2) is the group amount. In the above case, 21(param value + 1) groups are 0-100ms, 101-200ms, ... 1901-2000ms, 2000+ms 

- `apdex`. See [Apdex in WIKI](https://en.wikipedia.org/wiki/Apdex).
> service_apdex = from(Service.latency).apdex(name, status);

In this case, the apdex score of each service.
Parameter (1) is the service name, which reflects the Apdex threshold value loaded from service-apdex-threshold.yml in the config folder.
Parameter (2) is the status of this request. The status(success/failure) reflects the Apdex calculation.

- `p99`, `p95`, `p90`, `p75`, `p50`. See [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile).
> all_percentile = from(All.latency).percentile(10);

**percentile** is the first multiple-value metric, which has been introduced since 7.0.0. As a metric with multiple values, it could be queried through the `getMultipleLinearIntValues` GraphQL query.
In this case, see `p99`, `p95`, `p90`, `p75`, and `p50` of all incoming requests. The parameter is precise to a latency at p99, such as in the above case, and 120ms and 124ms are considered to produce the same response time.
Before 7.0.0, `p99`, `p95`, `p90`, `p75`, `p50` func(s) are used to calculate metrics separately. They are still supported in 7.x, but they are no longer recommended and are not included in the current official OAL script. 
> all_p99 = from(All.latency).p99(10);

In this case, the p99 value of all incoming requests. The parameter is precise to a latency at p99, such as in the above case, and 120ms and 124ms are considered to produce the same response time.

## Metrics name
The metrics name for storage implementor, alarm and query modules. The type inference is supported by core.

## Group
All metrics data will be grouped by Scope.ID and min-level TimeBucket. 

- In the `Endpoint` scope, the Scope.ID is same as the Endpoint ID (i.e. the unique ID based on service and its endpoint).

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
endpoint_avg = from(Endpoint.latency).avg()

// Calculate the p50, p75, p90, p95 and p99 of each Endpoint by 50 ms steps.
endpoint_percentile = from(Endpoint.latency).percentile(10)

// Calculate the percent of response status is true, for each service.
endpoint_success = from(Endpoint.*).filter(status == true).percent()

// Calculate the sum of response code in [404, 500, 503], for each service.
endpoint_abnormal = from(Endpoint.*).filter(responseCode in [404, 500, 503]).count()

// Calculate the sum of request type in [RequestType.RPC, RequestType.gRPC], for each service.
endpoint_rpc_calls_sum = from(Endpoint.*).filter(type in [RequestType.RPC, RequestType.gRPC]).count()

// Calculate the sum of endpoint name in ["/v1", "/v2"], for each service.
endpoint_url_sum = from(Endpoint.*).filter(name in ["/v1", "/v2"]).count()

// Calculate the sum of calls for each service.
endpoint_calls = from(Endpoint.*).count()

// Calculate the CPM with the GET method for each service.The value is made up with `tagKey:tagValue`.
service_cpm_http_get = from(Service.*).filter(tags contain "http.method:GET").cpm()

// Calculate the CPM with the HTTP method except for the GET method for each service.The value is made up with `tagKey:tagValue`.
service_cpm_http_other = from(Service.*).filter(tags not contain "http.method:GET").cpm()

disable(segment);
disable(endpoint_relation_server_side);
disable(top_n_database_statement);
```

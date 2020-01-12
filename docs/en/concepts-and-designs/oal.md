# Observability Analysis Language
Provide OAL(Observability Analysis Language) to analysis incoming data in streaming mode. 

OAL focuses on metrics in Service, Service Instance and Endpoint. Because of that, the language is easy to 
learn and use.


Since 6.3, the OAL engine is embedded in OAP server runtime, as `oal-rt`(OAL Runtime).
OAL scripts now locate in `/config` folder, user could simply change and reboot the server to make it effective.
But still, OAL script is compile language, OAL Runtime generates java codes dynamically.

You could open set `SW_OAL_ENGINE_DEBUG=Y` at system env, to see which classes generated.

## Grammar
Scripts should be named as `*.oal`
```
// Declare the metrics.
METRICS_NAME = from(SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])

// Disable hard code 
disable(METRICS_NAME);
```

## Scope
Primary **SCOPE**s are `All`, `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`.
Also there are some secondary scopes, which belongs to one primary scope. 

Read [Scope Definitions](scope-definitions.md), you can find all existing Scopes and Fields.


## Filter
Use filter to build the conditions for the value of fields, by using field name and expression. 

The expressions support to link by `and`, `or` and `(...)`. 
The OPs support `=`, `!=`, `>`, `<`, `in (v1, v2, ...`, `like "%..."`, with type detection based of field type. Trigger compile
 or code generation error if incompatible. 

## Aggregation Function
The default functions are provided by SkyWalking OAP core, and could implement more. 

Provided functions
- `longAvg`. The avg of all input per scope entity. The input field must be a long.
> instance_jvm_memory_max = from(ServiceInstanceJVMMemory.max).longAvg();

In this case, input are request of each ServiceInstanceJVMMemory scope, avg is based on field `max`.
- `doubleAvg`. The avg of all input per scope entity. The input field must be a double.
> instance_jvm_cpu = from(ServiceInstanceJVMCPU.usePercent).doubleAvg();

In this case, input are request of each ServiceInstanceJVMCPU scope, avg is based on field `usePercent`.
- `percent`. The number or ratio expressed as a fraction of 100, for the condition matched input.
> endpoint_percent = from(Endpoint.*).percent(status == true);

In this case, all input are requests of each endpoint, condition is `endpoint.status == true`.
- `sum`. The sum calls per scope entity.
> Service_Calls_Sum = from(Service.*).sum();

In this case, calls of each service. 

- `thermodynamic`. Read [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map)
> All_heatmap = from(All.latency).thermodynamic(100, 20);

In this case, thermodynamic heatmap of all incoming requests. 
The parameter (1) is the precision of latency calculation, such as in above case, 113ms and 193ms are considered same in the 101-200ms group.
The parameter (2) is the group amount. In above case, 21(param value + 1) groups are 0-100ms, 101-200ms, ... 1901-2000ms, 2000+ms 

- `apdex`. Read [Apdex in WIKI](https://en.wikipedia.org/wiki/Apdex)
> service_apdex = from(Service.latency).apdex(name, status);

In this case, apdex score of each service.
The parameter (1) is the service name, which effects the Apdex threshold value loaded from service-apdex-threshold.yml in the config folder.
The parameter (2) is the status of this request. The status(success/failure) effects the Apdex calculation.

- `p99`, `p95`, `p90`, `p75`, `p50`. Read [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile)
> all_percentile = from(All.latency).percentile(10);

**percentile** is the first multiple value metrics, introduced since 7.0.0. As having multiple values, it could be query through `getMultipleLinearIntValues` GraphQL query.
In this case, `p99`, `p95`, `p90`, `p75`, `p50` of all incoming request. The parameter is the precision of p99 latency calculation, such as in above case, 120ms and 124 are considered same.
Before 7.0.0, use `p99`, `p95`, `p90`, `p75`, `p50` func(s) to calculate metrics separately. Still supported in 7.x, but don't be recommended, and don't be included in official OAL script. 
> All_p99 = from(All.latency).p99(10);

In this case, p99 value of all incoming requests. The parameter is the precision of p99 latency calculation, such as in above case, 120ms and 124 are considered same.

## Metrics name
The metrics name for storage implementor, alarm and query modules. The type inference supported by core.

## Group
All metrics data will be grouped by Scope.ID and min-level TimeBucket. 

- In `Endpoint` scope, the Scope.ID = Endpoint id (the unique id based on service and its Endpoint)

## Disable
`Disable` is an advanced statement in OAL, which is only used in certain case.
Some of the aggregation and metrics are defined through core hard codes,
this `disable` statement is designed for make them de-active,
such as `segment`, `top_n_database_statement`.
In default, no one is being disable.

## Examples
```
// Caculate p99 of both Endpoint1 and Endpoint2
Endpoint_p99 = from(Endpoint.latency).filter(name in ("Endpoint1", "Endpoint2")).summary(0.99)

// Caculate p99 of Endpoint name started with `serv`
serv_Endpoint_p99 = from(Endpoint.latency).filter(name like ("serv%")).summary(0.99)

// Caculate the avg response time of each Endpoint
Endpoint_avg = from(Endpoint.latency).avg()

// Caculate the p50, p75, p90, p95 and p99 of each Endpoint by 50 ms steps.
Endpoint_percentile = from(Endpoint.latency).percentile(10)

// Caculate the percent of response status is true, for each service.
Endpoint_success = from(Endpoint.*).filter(status = "true").percent()

// Caculate the percent of response code in [200, 299], for each service.
Endpoint_200 = from(Endpoint.*).filter(responseCode like "2%").percent()

// Caculate the percent of response code in [500, 599], for each service.
Endpoint_500 = from(Endpoint.*).filter(responseCode like "5%").percent()

// Caculate the sum of calls for each service.
EndpointCalls = from(Endpoint.*).sum()

disable(segment);
disable(endpoint_relation_server_side);
disable(top_n_database_statement);
```

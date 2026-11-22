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

You can set `SW_DYNAMIC_CLASS_ENGINE_DEBUG=Y` at system env to dump generated `.class` files to disk.
See [Dynamic Code Generation and Debugging](../operation/dynamic-code-generation-debugging.md) for details.

## Grammar
Scripts should be named `*.oal`
```
// Declare the metrics.
METRICS_NAME = from([CAST] SCOPE.(* | FIELD[.FIELD ...]))
[.filter(FIELD OP VALUE)]...
.FUNCTION([PARAM[, PARAM]])
[.decorator("DECORATOR")];

// Disable hard code 
disable(METRICS_NAME);
```

OAL has no arithmetic, no math or string functions and no method calls: a filter compares a field with a literal or a
constant, and filters combine with AND only (no `||`, `!` or parentheses). A number is a non-negative integer, without a
sign, a decimal point or a hex prefix.

Comments are `// ...` to the end of the line, and `/* ... */`, which nest: a block of rules holding `/* notes */` can be
commented out whole. Each `/*` needs its own `*/`, so an unclosed one, or a `*/` outside a comment, fails the compile.
Anything the grammar does not accept, anywhere in the file, fails the compile.

## From
The **from** statement defines the data source of this OAL expression.

Primary **SCOPE**s are `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, and `EndpointRelation`.
There are also some secondary scopes which belong to a primary scope. 

See [Scope Definitions](scope-definitions.md), where you can find all existing Scopes and Fields.

A function that aggregates a field's value, such as `longAvg`, reads the field named in **from**, e.g. `from(Service.latency)`.
A function that does not, such as `cpm`, `count`, `percent`, `rate`, `labelCount` and `labelAvg`, takes `from(SCOPE.*)`;
a field there has no effect, and the compile logs a warning. `*` with a function that reads a field fails the compile, unless the
function takes the whole source, as a custom one may.


## Filter
Use filter to build conditions for the value of fields by using field name and expression.

The filter expressions run as a chain, generally connected with `logic AND`.
The value is of the field's type: a string for a `String` field, a constant of the field's own enum for an enum field (e.g.
`detectPoint == DetectPoint.SERVER`, not `"SERVER"`), `true` or `false` for a boolean field, and `null` for a field that is not a
primitive. An enum field compared with a string, and a string in `in [...]` with a field that cannot hold one, such as `layer in
["Layer.MESH"]`, fail the compile, as the comparison never holds; so does a comparison the generated code cannot make. A string with a
dot in `in [...]`, which named a constant before, is compared only with a field whose type the compile sees through its public getter,
not with a keyed attribute. `TYPE.NAME` names a static field of a class `TYPE` in the script's source package,
`org.apache.skywalking.oap.server.core.source` (`DetectPoint`, `RequestType`, `MQOperation`, `MemoryPoolType`, `GCPhase`,
`VirtualCacheOperation`, ...), or `org.apache.skywalking.oap.server.core.browser.source` for `browser.oal`
(`BrowserAppTrafficCategory`, `BrowserErrorCategory`). `Layer` is not in it. A field path `a.b` reads `getA().getB()`, `tag["key"]`
reads `getTag("key")`, and a boolean in a filter reads `isX()`. A path reads the source's attributes and their nested
ones only: a JDK value on the way (a `String`, a number, a collection, a map, an enum) has none, so `name.bytes` fails
the compile, also in a `from()` its function does not read.

A string takes Java's escapes, such as `"caf\u00e9"` and `"say \"hi\""`. A string literal is compared as written, quotes included:
`tag["etag"] == "v1"` matches `v1`, not the quoted ETag `"v1"`, which `tag["etag"] == "\"v1\""` matches; `in [...]` compares its
strings the same way, and its constants too with a public getter of a type that may hold a string. A string constant compares as
before: under `==`, when Javassist picks `StringMatch#match(String, String)`, that is when both the field and the constant are
declared `String`, a pair of quotes is taken off both values; otherwise, and under `!=`, they compare as written. A field declared as
`Object` or `CharSequence` may be compared with a string too, and a `byte`, `short` or `char` field compares as an `int`. The type
checks here read a field's type from its public getter; a keyed attribute, such as `tag["key"]`, is left to the compiled code.

Supported operators, by the value and the field type:

| Operator | Value | Field types |
|---|---|---|
| `==`, `!=` | `true`, `false` | `boolean`, read through `isX()` |
| `==` | number | `int`, `long` (and `byte`, `short`, `char` as `int`) |
| `!=` | number | `int` (and `byte`, `short`, `char`) |
| `>`, `>=`, `<`, `<=` | number | `int`, `long`, `double` (and `byte`, `short`, `char` as `int`) |
| `==`, `!=` | string | `String`; `Object`, `CharSequence` |
| `==` | `TYPE.NAME` | a field that is not a primitive, with a constant that is not one, e.g. an enum field with its enum's constant |
| `!=` | `TYPE.NAME` | as `==`, and an `int` field (and `byte`, `short`, `char`) with an `int` constant |
| `==`, `!=` | `null` | any field that is not a primitive |
| `in [...]` | numbers | `int`, `long` (and `byte`, `short`, `char`), e.g. `httpResponseStatusCode in [404, 500]` |
| `in [...]` | strings, or `TYPE.NAME` constants that are not primitives | a field that may hold them, e.g. `name in ["Endpoint1", "Endpoint2"]`; a list field, such as `tags`, uses `contain` |
| `like` | string that, with one pair of enclosing quotes taken off, starts and/or ends with `%`, other than `%` alone; `%%` matches every string that is not null | `String`, e.g. `name like "serv%"` |
| `contain`, `not contain` | string | `List<String>`, e.g. `tags contain "http.method:GET"`; a list of other elements never matches |

A list in `in [...]` holds one kind of value. With `==`, `!=` and `in`, a combination this table does not list fails the compile,
or compares the values as objects (`Objects.equals`) when the event comes: for types that never hold equal values, as
`layer != "MESH"` for a `Layer` field, `==` is always false and `!=` always true. With any other operator, a field type it does not
list fails the compile. This is the set this release supports; it grows as rules need more (propose an addition in an issue or PR).

The table describes the stock matchers. A class annotated `@FilterMatcher` with an operator's name, such as `inMatch`, replaces the
stock one, and receives the value as written, with none of the checks or encodings described here.

A number in a filter may use `_` separators and an `L` suffix, e.g. `1_000L`, but then no leading `0`, which Java would read as octal.
It is compared in the field's type, e.g. `filter(sideCar.internalRequestLatencyNanos >= 0)`. A number out of an `int` field's range
fails the compile, and one out of the `int` range is compared as a `long` with a field that is not a `double`, or whose type the
compile does not see, as a keyed one.

A filter reads a keyed attribute as its whole attribute, such as `tag["http.method"]`, or before the last segment of a dotted path,
with a key holding no dot and not as a boolean; any other keyed form fails the compile.

## Aggregation Function
The default functions are provided by the SkyWalking OAP core, and it is possible to implement additional functions. 

| Function | Reads from **from** | Arguments |
|---|---|---|
| `count`, `cpm` | nothing (`*`) | none |
| `sum`, `max`, `min`, `longAvg` | an `int` or `long` field | none |
| `doubleAvg`, `maxDouble`, `minDouble` | a number field | none |
| `percent` | nothing (`*`) | a condition |
| `rate` | nothing (`*`) | two conditions: numerator, denominator |
| `apdex` | an `int` latency | the service name field, the status field |
| `percentile2`, `percentile` | an `int` latency | the precision, a number of at least 1 |
| `histogram` | an `int` | the step, at least 1, and the number of steps, at least 0 |
| `labelCount` | nothing (`*`) | the label field; optionally the maximum label count, a number (default 50) |
| `labelAvg` | nothing (`*`) | the label field, the value field; at most 50 labels |

This is the set this release supports; it grows as rules need more (propose an addition in an issue or PR). A positive maximum label count bounds the labels of a metric as one OAP aggregates its
minute data; the hour and day downsampling, and the merge of metrics from other OAP nodes, may keep more, and `0` keeps every label.

An argument is a field, a number or `true`/`false`, or a condition where the function takes one. A number argument is an
`int`, or a `long` with an `L`. A string argument fails the compile.

A function takes at most two arguments, those it declares, in order; only a trailing optional one, such as the maximum label count of
`labelCount`, may be left out. A missing argument, a surplus one, or one of the wrong kind or type fails the compile.
The optional maximum label count is a number literal. A number literal for the precision of `percentile2` and `percentile` or the step
of `histogram` is at least 1, the number of steps at least 0, and the last bucket of `histogram`, their product, fits in an `int`.
A field may give them as well, when it is the same for every event of an entity: one metric merges its observations in a single width.
A number argument is written without `_` separators: without an `L` it is an `int`, and one out of the `int` range fails; with an `L` it is passed
to Javassist as written, with no check of its value or range (`0644L` is octal, `098L` reads as 98), for a function that takes a
`long`, such as the count of `labelAvg`. A condition passes to a function that takes one, such as `percent`. A
string argument, which was pasted into the generated code as Java code, fails the compile.
A function argument reads a keyed attribute only in a dotted path, such as `origin.label["region"]`, with a key holding no dot and not
as a boolean; `tag["key"]` alone fails the compile.
A field passes to a number parameter through a cast, as a `long` field does to the `int` of `percentile2`, and a `boolean` field read
through a `getX()` getter as 1 or 0; a `double` field, whose fraction the cast would cut off, does not pass to a `long` or `int`
parameter, when the compile sees its type through a public getter (a keyed one, such as `usage["cpu"]`, is cast, as the compile cannot see its type). A field of
a boxed type, such as `Integer`, does not pass to a primitive parameter.

Functions provided
- `longAvg`. The avg of all input per scope entity. The input field is an `int` or a `long`.
> instance_jvm_memory_max = from(ServiceInstanceJVMMemory.max).longAvg();

In this case, the input represents the request of each ServiceInstanceJVMMemory scope, and avg is based on field `max`.
- `doubleAvg`. The avg of all input per scope entity. The input field is a number.
> instance_jvm_cpu = from(ServiceInstanceJVMCPU.usePercent).doubleAvg();

In this case, the input represents the request of each ServiceInstanceJVMCPU scope, and avg is based on field `usePercent`.
- `sum`, `max`, `min`. The sum, the largest and the smallest input per scope entity, of an `int` or `long` field; `maxDouble` and
`minDouble` the largest and the smallest of a number field.
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

- `cpm`. Calls Per Minute. The total number of calls divided by the duration in minutes.
> service_cpm = from(Service.*).cpm();

In this case, the CPM of each service.

- `histogram`. See [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map).
> service_heatmap = from(Service.latency).histogram(100, 20);

In this case, the thermodynamic heatmap of all incoming requests. 
Parameter (1) is the step of the buckets, such as in the above case, where 113ms and 193ms are counted in the same bucket, `100`.
Parameter (2) is the number of steps. In the above case, the 21 (param value + 1) buckets, keyed `0`, `100`, ..., `2000`, hold
0-99ms, 100-199ms, ... 1900-1999ms, and 2000ms and over.

- `apdex`. See [Apdex in WIKI](https://en.wikipedia.org/wiki/Apdex).
> service_apdex = from(Service.latency).apdex(name, status);

In this case, the apdex score of each service.
Parameter (1) is the service name, which reflects the Apdex threshold value loaded from service-apdex-threshold.yml in the config folder.
Parameter (2) is the status of this request. The status(success/failure) reflects the Apdex calculation.

- `percentile2`, `percentile`. Their outputs are `p50`, `p75`, `p90`, `p95` and `p99`. See [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile).
> service_percentile = from(Service.latency).percentile2(10);

**percentile (deprecated since 10.0.0)** is the first multiple-value metric, which has been introduced since 7.0.0. As a metric with multiple values, it could be queried through the `getMultipleLinearIntValues` GraphQL query.
**percentile2** Since 10.0.0, the `percentile` function has been instead by `percentile2`. The `percentile2` function is a labeled-value metric with default label name `p` and label values `50`,`75`,`90`,`95`,`99`.
In this case, see `p99`, `p95`, `p90`, `p75`, and `p50` of all incoming requests. The parameter is precise to a latency at p99, such as in the above case, and 120ms and 124ms are considered to produce the same response time.

- `labelCount`. The count of the label value.
> drop_reason_count = from(CiliumService.*).filter(verdict == "dropped").labelCount(dropReason, 100);

In this case, the count of the drop reason of each Cilium service, max support calculate `100` reasons(optional configuration). 

- `labelAvg`. The avg of the label value.
> browser_resource_duration_avg = from(BrowserAppResourcePerf.*).labelAvg(name, duration);

In this case, the avg of the duration of each browser resource file, for at most 50 of them.

## Metrics name
The metrics name for storage implementor, alarm and query modules. The type inference is supported by core.

## Group
All metrics data will be grouped by Scope.ID and min-level TimeBucket.

- In the `Endpoint` scope, the Scope.ID is same as the Endpoint ID (i.e. the unique ID based on service and its endpoint).

The `Minute` level aggregation is enforced by default. Additional time bucket aggregations (`Hour`, `Day`) are also enabled by default and can be configured via `core/downsampling` in `application.yml`.

## Cast
Fields of source are static type. In some cases, the type required by the filter expression and aggregation function doesn't 
match the type in the source, such as tag value in the source is String type, most aggregation calculation requires numeric.

Cast expression is provided to do so. 
- `(str->long)` or `(long)`, cast string type into long.
- `(str->int)` or `(int)`, cast string type into int.

```
mq_consume_latency = from((str->long)Service.tag["transmission.latency"]).longAvg(); // the value of tag is string type.
```

A cast reads a `String` field; on a field of another type it fails the compile of a function that reads the value, such as
`longAvg`, while `count` reads none.

A cast is supported in the **from** statement, `from((cast)source.attre)`, only. In a filter or a function argument it fails the compile:
a value it cannot parse would stop the event for the other metrics of the source. Guard the value it reads, as `core.oal` does with
`.filter(tag["transmission.latency"] != null)`: that guards a missing tag, and every value present has to parse as a decimal integer.

## Decorator
`decorator` is to select a specific decorator to decorate the source for the metrics.
> service_resp_time = from(Service.latency).longAvg().decorator("ServiceDecorator");

In this case, the `ServiceDecorator` is the `Java Class simple name` which used to decorate the source of `Service` before the aggregation function.
This function is used to add additional attributes to the metrics. More details, see [Metrics Additional Attributes](metrics-additional-attributes.md).

The OAP provides `ServiceDecorator` (for `Service`), `EndpointDecorator` (for `Endpoint`) and `K8SServiceDecorator` (for
`K8SService`); only these sources can be decorated. A name the OAP does not register fails each event. This is the set this release supports; it grows as rules need more (propose an addition in an issue or PR).

## Disable
`Disable` is an advanced statement in OAL, which is only used in certain cases.
Some of the aggregation and metrics are defined through core hard codes. Examples include `segment` and `top_n_database_statement`.
This `disable` statement is designed to render them inactive.
By default, none of them are disabled.

**NOTICE**, all disable statements should be in `oal/disable.oal` script file, which loads before any stream is defined.
A disable in another script of a stream already defined, as `browser_error_log` is when `oal/browser.oal` loads, has no effect, and the
compile logs a warning; repeating a disable that took effect is fine. A disabled OAL metric is still evaluated for each event, so a decorator it names still decorates the source, but its stream
does not run. A name that no stream of the OAP carries, a hard-coded one or a metric of a loaded OAL script, has no effect,
and the OAP logs a warning when it has started: a disable does not apply to a MAL metric, nor to one of a script the OAP does not load.

## Examples
```
// Calculate percentile(s) of both Endpoint1 and Endpoint2
endpoint_p99 = from(Endpoint.latency).filter(name in ["Endpoint1", "Endpoint2"]).percentile2(10);

// Calculate percentile(s) of Endpoint name started with `serv`
serv_Endpoint_p99 = from(Endpoint.latency).filter(name like "serv%").percentile2(10);

// Calculate the avg response time of each Endpoint
endpoint_resp_time = from(Endpoint.latency).longAvg();

// Calculate the p50, p75, p90, p95 and p99 of each Endpoint by 10 ms steps.
endpoint_percentile = from(Endpoint.latency).percentile2(10);

// Calculate the percent of response status is true, for each service.
endpoint_success = from(Endpoint.*).percent(status == true);

// Calculate the sum of response code in [404, 500, 503], for each service.
endpoint_abnormal = from(Endpoint.*).filter(httpResponseStatusCode in [404, 500, 503]).count();

// Calculate the sum of request type in [RequestType.RPC, RequestType.gRPC], for each service.
endpoint_rpc_calls_sum = from(Endpoint.*).filter(type in [RequestType.RPC, RequestType.gRPC]).count();

// Calculate the sum of endpoint name in ["/v1", "/v2"], for each service.
endpoint_url_sum = from(Endpoint.*).filter(name in ["/v1", "/v2"]).count();

// Calculate the sum of calls for each service.
endpoint_calls = from(Endpoint.*).count();

// Calculate the CPM with the GET method for each service.The value is made up with `tagKey:tagValue`.
// Option 1, use `tags contain`.
service_cpm_http_get = from(Service.*).filter(tags contain "http.method:GET").cpm();
// Option 2, use `tag[key]`.
// service_cpm_http_get = from(Service.*).filter(tag["http.method"] == "GET").cpm();

// Calculate the CPM with the HTTP method except for the GET method for each service.The value is made up with `tagKey:tagValue`.
service_cpm_http_other = from(Service.*).filter(tags not contain "http.method:GET").cpm();
```

In `oal/disable.oal`:
```
disable(segment);
disable(endpoint_relation_server_side);
disable(top_n_database_statement);
```

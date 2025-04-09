# PromQL Service
PromQL([Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)) Service
exposes Prometheus Querying HTTP APIs including the bundled PromQL expression system.
Third-party systems or visualization platforms that already support PromQL (such as Grafana), 
could obtain metrics through PromQL Service.

As SkyWalking and Prometheus have fundamental differences in metrics classification, format, storage, etc. 
The PromQL Service supported will be a subset of the complete PromQL.

## Details Of Supported Protocol
The following doc describes the details of the supported protocol and compared it to the PromQL official documentation.
If not mentioned, it will not be supported by default.

### Time series Selectors
#### Instant Vector Selectors
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/basics/#instant-vector-selectors)

For example: select metric `service_cpm` which the service is `$service` and the layer is `$layer`.
```text
service_cpm{service='$service', layer='$layer'}
```
**Note: The label matching operators only support `=` instead of regular expressions.**

#### Range Vector Selectors
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/basics/#range-vector-selectors)

For example: select metric `service_cpm` which the service is `$service` and the layer is `$layer` within the last 5 minutes.
```text
service_cpm{service='$service', layer='$layer'}[5m]
```

#### Time Durations
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/basics/#time-durations)

| Unit | Definition   | Support |
|------|--------------|---------|
| ms   | milliseconds | yes     |
| s    | seconds      | yes     |
| m    | minutes      | yes     |
| h    | hours        | yes     |
| d    | days         | yes     |
| w    | weeks        | yes     |
| y    | years        | **no**  |

### Binary operators
#### Arithmetic binary operators

[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/operators/#arithmetic-binary-operators)

| Operator | Definition           | Support |
|----------|----------------------|---------|
| +        | addition             | yes     |
| -        | subtraction          | yes     |
| *        | multiplication       | yes     |
| /        | division             | yes     |
| %        | modulo               | yes     |
| ^        | power/exponentiation | **no**  |

##### Between two scalars
For example:
```text
1 + 2
```

##### Between an instant vector and a scalar
For example:
```text
service_cpm{service='$service', layer='$layer'} / 100
```

##### Between two instant vectors
For example:
```text
service_cpm{service='$service', layer='$layer'} + service_cpm{service='$service', layer='$layer'}
```

**Note: The operations between vectors require the same metric and labels, and don't support [Vector matching](https://prometheus.io/docs/prometheus/latest/querying/operators/#vector-matching).**

#### Comparison binary operators

[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/operators/#comparison-binary-operators)

| Operator | Definition       | Support |
|----------|------------------|---------|
| ==       | equal            | yes     |
| !=       | not-equal        | yes     |
| \>       | greater-than     | yes     |
| <        | less-than        | yes     |
| \>=      | greater-or-equal | yes     |
| <=       | less-or-equal)   | yes     |
##### Between two scalars
For example:
```text
1 > bool 2
```

##### Between an instant vector and a scalar
For example:
```text
service_cpm{service='$service', layer='$layer'} > 1
```

##### Between two instant vectors
For example:
```text
service_cpm{service='service_A', layer='$layer'} > service_cpm{service='service_B', layer='$layer'}
```

### Aggregation operators
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/operators/#aggregation-operators)

| Operator | Definition                            | Support |
|----------|---------------------------------------|---------|
| sum      | calculate sum over dimensions         | yes     |
| min      | select minimum over dimensions        | yes     |
| max      | select maximum over dimensions        | yes     |
| avg      | calculate the average over dimensions | yes     |

For example:

If the metric `http_requests_total` had time series that fan out by `service`, `service_instance_id`, and `group` labels,
we could calculate the total number of seen HTTP requests per service and group over all service instances via:

```
sum by (service, group) (http_requests_total{service='$service', layer='$layer'})
```
Which is equivalent to:
```
sum without (service_instance_id) (http_requests_total{service='$service', layer='$layer'})
```
If we are just interested in the total of HTTP requests we have seen in all services, we could simply write:
```
sum(http_requests_total{service='$service', layer='$layer'})
```

### HTTP API

#### Expression queries

##### Instant queries

[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#instant-queries)

```text
GET|POST /api/v1/query
```

| Parameter | Definition                                                                                                                                                                            | Support | Optional   |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|------------|
| query     | prometheus expression                                                                                                                                                                 | yes     | no         |
| time      | **The latest metrics value from current time to this time is returned. If time is empty, the default look-back time is 2 minutes.** time format: RFC3399 or unix_timestamp in seconds | yes     | yes        |
| timeout   | evaluation timeout                                                                                                                                                                    | **no**  | **ignore** |

For example:
```text
/api/v1/query?query=service_cpm{service='agent::songs', layer='GENERAL'}
```

Result:
```json
{
    "status": "success",
    "data": {
        "resultType": "vector",
        "result": [
            {
                "metric": {
                    "__name__": "service_cpm",
                    "layer": "GENERAL",
                    "scope": "Service",
                    "service": "agent::songs"
                },
                "value": [
                    1677548400,
                    "6"
                ]
            }
        ]
    }
}
```

We can also use [Range Vector Selectors](#range-vector-selectors) in the instant query.
```
/api/v1/query?query=service_cpm{service='agent::songs', layer='GENERAL'}[5m]
```

the result is the same as the [Range queries](#range-queries).

##### Range queries

[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#range-queries)

```text
GET|POST /api/v1/query_range
```

| Parameter | Definition                                                                           | Support | Optional   |
|-----------|--------------------------------------------------------------------------------------|---------|------------|
| query     | prometheus expression                                                                | yes     | no         |
| start     | start timestamp, format: RFC3399 or unix_timestamp in seconds                        | yes     | no         |
| end       | end timestamp, format: RFC3399 or unix_timestamp in seconds                          | yes     | no         |
| step      | **SkyWalking will automatically fit Step(DAY, HOUR, MINUTE) through start and end.** | **no**  | **ignore** |
| timeout   | evaluation timeout                                                                   | **no**  | **ignore** |

For example:
```text
/api/v1/query_range?query=service_cpm{service='agent::songs', layer='GENERAL'}&start=1677479336&end=1677479636
```

Result:
```json
{
    "status": "success",
    "data": {
        "resultType": "matrix",
        "result": [
            {
                "metric": {
                    "__name__": "service_cpm",
                    "layer": "GENERAL",
                    "scope": "Service",
                    "service": "agent::songs"
                },
                "values": [
                    [
                        1677479280,
                        "18"
                    ],
                    [
                        1677479340,
                        "18"
                    ],
                    [
                        1677479400,
                        "18"
                    ],
                    [
                        1677479460,
                        "18"
                    ],
                    [
                        1677479520,
                        "18"
                    ],
                    [
                        1677479580,
                        "18"
                    ]
                ]
            }
        ]
    }
}
```

#### Querying metadata

##### Finding series by label matchers
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#finding-series-by-label-matchers)

```text
GET|POST /api/v1/series
```

| Parameter | Definition                                          | Support | Optional |
|-----------|-----------------------------------------------------|---------|----------|
| match[]   | series selector                                     | yes     | no       |
| start     | start, format: RFC3399 or unix_timestamp in seconds | yes     | no       |
| end       | end, format: RFC3399 or unix_timestamp in seconds   | yes     | no       |
| limit     | integer, maximum number of returned series          | yes     | yes      |

**Note: SkyWalking's metadata exists in the following metrics(traffics):**

| Name             | Require Labels | Optional Labels          | Support Label Match                                 |
|------------------|----------------|--------------------------|-----------------------------------------------------|
| service_traffic  | layer          | service, limit           | =, (only service label support !=, =~, !~)          |
| instance_traffic | layer, service | service_instance, limit  | =, (only service_instance label support !=, =~, !~) |
| endpoint_traffic | layer, service | endpoint, keyword, limit | =, (only endpoint label support !=, =~, !~)         |

**If the `limit` is not set by parameter or label, the default value is 100.**

For example:
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL'}&start=1677479336&end=1677479636&limit=5
```
or
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL', limit='5'}&start=1677479336&end=1677479636
```
If the `limit` also set in the query parameter, will return the min number of the two.

Result:
```json
{
    "status": "success",
    "data": [
        {
            "__name__": "service_traffic",
            "service": "agent::songs",
            "scope": "Service",
            "layer": "GENERAL"
        },
        {
            "__name__": "service_traffic",
            "service": "agent::recommendation",
            "scope": "Service",
            "layer": "GENERAL"
        },
        {
            "__name__": "service_traffic",
            "service": "agent::app",
            "scope": "Service",
            "layer": "GENERAL"
        },
        {
            "__name__": "service_traffic",
            "service": "agent::gateway",
            "scope": "Service",
            "layer": "GENERAL"
        },
        {
            "__name__": "service_traffic",
            "service": "agent::frontend",
            "scope": "Service",
            "layer": "GENERAL"
        }
    ]
}
```

- You can use the `service` label to filter the service_traffic result.
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL', service='agent::songs'}&start=1677479336&end=1677479636
```
use regex:
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL', service=~'agent::songs|agent::recommendation'}&start=1677479336&end=1677479636
```
- You can use the `service_instance` label to filter the instance_traffic result.
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL', service='agent::songs', service_instance=~'instance1|instance2'}&start=1677479336&end=1677479636
```
- You can use the `endpoint` label to filter the endpoint_traffic result.
```text
/api/v1/series?match[]=service_traffic{layer='GENERAL', service='agent::songs', endpoint=~'endpoint1|endpoint2'}&start=1677479336&end=1677479636
```


#### Getting label names
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#getting-label-names)

```text
GET|POST /api/v1/labels
```

| Parameter | Definition                                                                      | Support | Optional |
|-----------|---------------------------------------------------------------------------------|---------|----------|
| match[]   | series selector                                                                 | yes     | yes      |
| start     | start, format: RFC3399 or unix_timestamp in seconds                             | **no**  | yes      |
| end       | end timestamp, if end time is not present, use current time as default end time | yes     | yes      |
| limit     | integer, maximum number of returned labels, default 100                         | yes     | yes      |

For example:
```text
/api/v1/labels?match[]=instance_jvm_cpu'
```

Result:
```json
{
    "status": "success",
    "data": [
        "layer",
        "service",
        "top_n",
        "order",
        "service_instance",
        "parent_service"
    ]
}
```

#### Querying label values
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#querying-label-values)

```text
GET /api/v1/label/<label_name>/values
```

| Parameter | Definition                                                                                                          | Support | Optional |
|-----------|---------------------------------------------------------------------------------------------------------------------|---------|----------|
| match[]   | series selector                                                                                                     | yes     | yes      |
| start     | start, format: RFC3399 or unix_timestamp in seconds                                                                 | **no**  | yes      |
| end       | end, format: RFC3399 or unix_timestamp in seconds, if end time is not present, use current time as default end time | yes     | yes      |
| limit     | integer, maximum number of returned label values, default 100                                                       | yes     | yes      |

For example:
```text
/api/v1/label/__name__/values
``` 

Result:
```json
{
  "status": "success",
  "data": [
    "meter_mysql_instance_qps",
    "service_cpm",
    "envoy_cluster_up_rq_active",
    "instance_jvm_class_loaded_class_count",
    "k8s_cluster_memory_requests",
    "meter_vm_memory_used",
    "meter_apisix_sv_bandwidth_unmatched",
    "meter_vm_memory_total",
    "instance_jvm_thread_live_count",
    "instance_jvm_thread_timed_waiting_state_thread_count",
    "browser_app_page_first_pack_percentile",
    "instance_clr_max_worker_threads",
    ...
  ]
}
```

For metadata metrics:

| Name             | Require Labels | Optional Labels          | Support Label Match                                 |
|------------------|----------------|--------------------------|-----------------------------------------------------|
| service_traffic  | layer          | service, limit           | =, (only service label support !=, =~, !~)          |
| instance_traffic | layer, service | service_instance, limit  | =, (only service_instance label support !=, =~, !~) |
| endpoint_traffic | layer, service | endpoint, keyword, limit | =, (only endpoint label support !=, =~, !~)         |

For example:
- If you want to query the label values of the `service` label in the `service_traffic` metric:
```text
/api/v1/label/service/values?match[]=service_traffic{layer='GENERAL', service='agent::songs|agent::recommendation'}
```
- If you want to query the label values of the `service_instance` label in the `instance_traffic` metric:
```text
/api/v1/label/service_instance/values?match[]=instance_traffic{layer='GENERAL', service='agent::songs', service_instance='instance1|instance2'}
```
- If you want to query the label values of the `endpoint` label in the `endpoint_traffic` metric:
```text
/api/v1/label/endpoint/values?match[]=endpoint_traffic{layer='GENERAL', service='agent::songs', endpoint='endpoint1|endpoint2'}
```

#### Querying metric metadata
[Prometheus Docs Reference](https://prometheus.io/docs/prometheus/latest/querying/api/#querying-metric-metadata)

```text
GET /api/v1/metadata
```

| Parameter | Definition                                  | Support | Optional |
|-----------|---------------------------------------------|---------|----------|
| limit     | maximum number of metrics to return         | yes     | **yes**  |
| metric    | **metric name, support regular expression** | yes     | **yes**  |

For example:
```text
/api/v1/metadata?limit=10
```

Result:
```json
{
  "status": "success",
  "data": {
    "meter_mysql_instance_qps": [
      {
        "type": "gauge",
        "help": "",
        "unit": ""
      }
    ],
    "meter_apisix_sv_bandwidth_unmatched": [
      {
        "type": "gauge",
        "help": "",
        "unit": ""
      }
    ],
    "service_cpm": [
      {
        "type": "gauge",
        "help": "",
        "unit": ""
      }
    ],
    ...
  }
}
```

## Metrics Type For Query

### Supported Metrics Scope(Catalog)
Not all scopes are supported for now, please check the following table:

| Scope                   | Support |
|-------------------------|---------|
| Service                 | yes     |
| ServiceInstance         | yes     |
| Endpoint                | yes     |
| ServiceRelation         | yes     |
| ServiceInstanceRelation | yes     |
| EndpointRelation        | yes     |
| Process                 | no      |
| ProcessRelation         | no      |

All Scopes could be found [here](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/query/enumeration/Scope.java).

### General labels
Each metric contains general labels: [layer](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java).
Different metrics will have different labels depending on their Scope and metric value type.

| Query Labels                                                                      | Scope                   | Expression Example                                                                                                                                                                                                 |
|-----------------------------------------------------------------------------------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| layer, service                                                                    | Service                 | service_cpm{service='$service', layer='$layer'}                                                                                                                                                                    |
| layer, service, service_instance                                                  | ServiceInstance         | service_instance_cpm{service='$service', service_instance='$service_instance', layer='$layer'}                                                                                                                     |
| layer, service, endpoint                                                          | Endpoint                | endpoint_cpm{service='$service', endpoint='$endpoint', layer='$layer'}                                                                                                                                             |
| layer, service, dest_service, dest_layer                                          | ServiceRelation         | service_relation_metric{service='$service', layer='$layer', dest_layer='$dest_layer', dest_service='$dest_service'}                                                                                                |
| layer, service, dest_service, dest_layer, service_instance, dest_service_instance | ServiceInstanceRelation | service_instance_relation_metric{service='$service', layer='$layer', dest_layer='$dest_layer', dest_service='$dest_service', dest_service_instance='$dest_service_instance', service_instance='$service_instance'} |
| layer, service, dest_service, dest_layer, endpoint, dest_endpoint                 | EndpointRelation        | endpoint_relation_metric{service='$service', endpoint='$endpoint', layer='$layer', dest_layer='$dest_layer', dest_service='$dest_service', dest_endpoint='$dest_endpoint'}                                         |



### Common Value Metrics
- Query Labels: 
```text
{General labels}
```

- Expression Example: 
```text
service_cpm{service='agent::songs', layer='GENERAL'}
```

- Result (Instant Query):
```json
{
    "status": "success",
    "data": {
        "resultType": "vector",
        "result": [
            {
                "metric": {
                    "__name__": "service_cpm",
                    "layer": "GENERAL",
                    "scope": "Service",
                    "service": "agent::songs"
                },
                "value": [
                    1677490740,
                    "3"
                ]
            }
        ]
    }
}
```


### Labeled Value Metrics
- Query Labels:
```text
--{General labels}
--metric labels: Used to filter the value labels to be returned
```

- Expression Example:
```text
service_percentile{service='agent::songs', layer='GENERAL', p='50,75,90'}
```

- Result (Instant Query):
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "service_percentile",
          "p": "50",
          "layer": "GENERAL",
          "scope": "Service",
          "service": "agent::songs"
        },
        "value": [
          1677493380,
          "0"
        ]
      },
      {
        "metric": {
          "__name__": "service_percentile",
          "p": "75",
          "layer": "GENERAL",
          "scope": "Service",
          "service": "agent::songs"
        },
        "value": [
          1677493380,
          "0"
        ]
      },
      {
        "metric": {
          "__name__": "service_percentile",
          "p": "90",
          "layer": "GENERAL",
          "scope": "Service",
          "service": "agent::songs"
        },
        "value": [
          1677493380,
          "0"
        ]
      }
    ]
  }
}
```

### Sort Metrics
- Query Labels:
```text
--parent_service: <optional> Name of the parent service.
--top_n: The max number of the selected metric value
--order: ASC/DES
```

- Expression Example:
```text
service_instance_cpm{parent_service='agent::songs', layer='GENERAL',  top_n='10', order='DES'}
```

- Result (Instant Query):
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "service_instance_cpm",
          "layer": "GENERAL",
          "scope": "ServiceInstance",
          "service_instance": "651db53c0e3843d8b9c4c53a90b4992a@10.4.0.28"
        },
        "value": [
          1677494280,
          "14"
        ]
      },
      {
        "metric": {
          "__name__": "service_instance_cpm",
          "layer": "GENERAL",
          "scope": "ServiceInstance",
          "service_instance": "4c04cf44d6bd408880556aa3c2cfb620@10.4.0.232"
        },
        "value": [
          1677494280,
          "6"
        ]
      },
      {
        "metric": {
          "__name__": "service_instance_cpm",
          "layer": "GENERAL",
          "scope": "ServiceInstance",
          "service_instance": "f5ac8ead31af4e6795cae761729a2742@10.4.0.236"
        },
        "value": [
          1677494280,
          "5"
        ]
      }
    ]
  }
}
```

### Sampled Records

- Query Labels:
```text
--parent_service: Name of the parent service
--top_n: The max number of the selected records value
--order: ASC/DES
```

- Expression Example:
```text
top_n_database_statement{parent_service='localhost:-1', layer='VIRTUAL_DATABASE',  top_n='10', order='DES'}
```

- Result (Instant Query):
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "top_n_database_statement",
          "layer": "VIRTUAL_DATABASE",
          "scope": "Service",
          "record": "select song0_.id as id1_0_, song0_.artist as artist2_0_, song0_.genre as genre3_0_, song0_.liked as liked4_0_, song0_.name as name5_0_ from song song0_ where song0_.liked>?"
        },
        "value": [
          1677501360,
          "1"
        ]
      },
      {
        "metric": {
          "__name__": "top_n_database_statement",
          "layer": "VIRTUAL_DATABASE",
          "scope": "Service",
          "record": "select song0_.id as id1_0_, song0_.artist as artist2_0_, song0_.genre as genre3_0_, song0_.liked as liked4_0_, song0_.name as name5_0_ from song song0_ where song0_.liked>?"
        },
        "value": [
          1677501360,
          "1"
        ]
      },
      {
        "metric": {
          "__name__": "top_n_database_statement",
          "layer": "VIRTUAL_DATABASE",
          "scope": "Service",
          "record": "select song0_.id as id1_0_, song0_.artist as artist2_0_, song0_.genre as genre3_0_, song0_.liked as liked4_0_, song0_.name as name5_0_ from song song0_ where song0_.liked>?"
        },
        "value": [
          1677501360,
          "1"
        ]
      }
    ]
  }
}
```


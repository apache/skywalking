# Metrics Additional Attributes
SkyWalking provides [OAL](oal.md) and [MAL](mal.md) to analyze the data source and generate the metrics. 
Generally, the metrics fields in the storage include `name`, `entity_id`, `service_id` `value` and `timebucket` etc.
In most cases, these fields are enough to query a metric. However, sometimes we need to add more fields to the metrics to make the query more flexible.
A typical case is the `topN` query, when we query the top N services by the metric value. 
If we want to filter the services by `Layer` or `Group` or other specific conditions, it is impossible to do this with the current metrics fields.

Since 10.2.0, SkyWalking supports the metrics attributes through source decorate. 
SkyWalking provides additional attributes(`attr0...attr5`) fields to the metrics and source. 
By default, these fields are empty, we can fill them by set a specific decorate logic for source. 
According to the difference between the `OAL` and `MAL`, the usage of decorate is different.

**Notice:** For now, the metrics attributes only support the `service metrics` and `non-labeled` metrics.

## OAL Source Decorate
In the OAL script, you can use the [decorator](oal.md#decorator) function to specify a Java Class to decorate the source,
and the Java Class must follow the following rules:
- The Class must implement the [ISourceDecorator](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/ISourceDecorator.java) interface.
- The Class package must be under the `org.apache.skywalking.*`.

### Default Decorator
SkyWalking provides some default implementation of decorator:

- [ServiceDecorator](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/source/ServiceDecorator.java) which set the `attr0` to the service `Layer`.
The following OAL metrics had been decorated by the `ServiceDecorator` by default:
```text
// Service scope metrics
service_resp_time = from(Service.latency).longAvg().decorator("ServiceDecorator");
service_sla = from(Service.*).percent(status == true).decorator("ServiceDecorator");
service_cpm = from(Service.*).cpm().decorator("ServiceDecorator");
service_apdex = from(Service.latency).apdex(name, status).decorator("ServiceDecorator");
```

- [EndpointDecorator](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/source/EndpointDecorator.java) which set the `attr0` to the endpoint `Layer`.
The following OAL metrics had been decorated by the `EndpointDecorator` by default:
```text
endpoint_cpm = from(Endpoint.*).cpm().decorator("EndpointDecorator");
endpoint_resp_time = from(Endpoint.latency).longAvg().decorator("EndpointDecorator");
endpoint_sla = from(Endpoint.*).percent(status == true).decorator("EndpointDecorator");
```

- [K8SServiceDecorator](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/source/K8SServiceDecorator.java) which set the `attr0` to the k8s service `Layer`.
The following OAL metrics had been decorated by the `K8SServiceDecorator` by default:
```text
kubernetes_service_http_call_cpm = from(K8SService.*).filter(detectPoint == DetectPoint.SERVER).filter(type == "protocol").filter(protocol.type == "http").cpm().decorator("K8SServiceDecorator");
kubernetes_service_http_call_time = from(K8SService.protocol.http.latency).filter(detectPoint == DetectPoint.SERVER).filter(type == "protocol").filter(protocol.type == "http").longAvg().decorator("K8SServiceDecorator");
kubernetes_service_http_call_successful_rate = from(K8SService.*).filter(detectPoint == DetectPoint.SERVER).filter(type == "protocol").filter(protocol.type == "http").percent(protocol.success == true).decorator("K8SServiceDecorator");
kubernetes_service_apdex = from(K8SService.protocol.http.latency).filter(detectPoint == DetectPoint.SERVER).filter(type == "protocol").filter(protocol.type == "http").apdex(name, protocol.success).decorator("K8SServiceDecorator");
```    

## MAL Source Decorate
In the MAL script, you can use the [decorate](mal.md#decorate-function) function to decorate the source, and must follow the following rules:
- The decorate function must after service() function.
- Not supported for histogram metrics.

SkyWalking does not provide a default script for MAL, you can refer to the following example to set the `attr0` to the service `Layer`:
```text
  - name: cpu_load1
    exp: (node_load1 * 100).service(['node_identifier_host_name'] , Layer.OS_LINUX).decorate({ me -> me.attr0 = me.layer.name()})
```

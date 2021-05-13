# Sending Envoy Metrics to SkyWalking OAP Server Example

This is an example of sending [Envoy Stats](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/observability/statistics#arch-overview-statistics) to SkyWalking OAP server
through [Metric Service](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v2/config/metrics/v2/metrics_service.proto).

## Running the example

The example requires `docker` and `docker-compose` to be installed in your local. It fetches images from Docker Hub.

Note that in ths setup, we override the [`log4j2.xml`](log4j2.xml) config to set the `org.apache.skywalking.oap.server.receiver.envoy` logger level to `DEBUG`. This enables us to see the messages sent by Envoy to SkyWalking OAP server.

```
$ make up
$ docker-compose logs -f skywalking
$ # Please wait for a moment until SkyWalking is ready and Envoy starts sending the stats. You will see similar messages like the following:
skywalking_1  | 2019-08-31 23:57:40,672 - org.apache.skywalking.oap.server.receiver.envoy.MetricServiceGRPCHandler -26870 [grpc-default-executor-0] DEBUG [] - Received msg identifier {
skywalking_1  |   node {
skywalking_1  |     id: "ingress"
skywalking_1  |     cluster: "envoy-proxy"
skywalking_1  |     metadata {
skywalking_1  |       fields {
skywalking_1  |         key: "skywalking"
skywalking_1  |         value {
skywalking_1  |           string_value: "iscool"
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |       fields {
skywalking_1  |         key: "envoy"
skywalking_1  |         value {
skywalking_1  |           string_value: "isawesome"
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |     }
skywalking_1  |     locality {
skywalking_1  |       region: "ap-southeast-1"
skywalking_1  |       zone: "zone1"
skywalking_1  |       sub_zone: "subzone1"
skywalking_1  |     }
skywalking_1  |     build_version: "e349fb6139e4b7a59a9a359be0ea45dd61e589c5/1.11.1/Clean/RELEASE/BoringSSL"
skywalking_1  |   }
skywalking_1  | }
skywalking_1  | envoy_metrics {
skywalking_1  |   name: "cluster.service_skywalking.update_success"
skywalking_1  |   type: COUNTER
skywalking_1  |   metric {
skywalking_1  |     counter {
skywalking_1  |       value: 2.0
skywalking_1  |     }
skywalking_1  |     timestamp_ms: 1567295859556
skywalking_1  |   }
skywalking_1  | }
...

$ # To tear down:
$ make down
```

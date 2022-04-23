# Sending Envoy Metrics to SkyWalking OAP Server Example

This is an example of sending [Envoy Stats](https://www.envoyproxy.io/docs/envoy/v1.19.1/intro/arch_overview/observability/statistics) to the SkyWalking OAP server
through Metric Service [v2](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v2/config/metrics/v2/metrics_service.proto) and [v3](https://www.envoyproxy.io/docs/envoy/v1.19.1/api-v3/config/metrics/v3/metrics_service.proto).

## Running the example

The example requires `docker` and `docker-compose` to be installed in your local system. It fetches images from Docker Hub.

Note that in this setup, we override the [`log4j2.xml`](log4j2.xml) config to set the `org.apache.skywalking.oap.server.receiver.envoy` logger level to `DEBUG`. This enables us to see the messages sent by Envoy to the SkyWalking OAP server.

You can also find the Envoy Metric Service V3 API example in [docker-compose-envoy-v3-api.yaml](./docker-compose-envoy-v3-api.yaml)
```
$ make up
$ docker-compose logs -f skywalking
$ # Please wait for a moment until SkyWalking is ready and Envoy starts sending the stats. You will see similar messages like the following:
skywalking_1  | 2021-07-23 13:25:30,683 - org.apache.skywalking.oap.server.receiver.envoy.MetricServiceGRPCHandler -19437 [grpcServerPool-1-thread-2] DEBUG [] - Received msg identifier {
skywalking_1  |   node {
skywalking_1  |     id: "ingress"
skywalking_1  |     cluster: "envoy-proxy"
skywalking_1  |     metadata {
skywalking_1  |       fields {
skywalking_1  |         key: "LABELS"
skywalking_1  |         value {
skywalking_1  |           struct_value {
skywalking_1  |             fields {
skywalking_1  |               key: "app"
skywalking_1  |               value {
skywalking_1  |                 string_value: "test-app"
skywalking_1  |               }
skywalking_1  |             }
skywalking_1  |           }
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |       fields {
skywalking_1  |         key: "NAME"
skywalking_1  |         value {
skywalking_1  |           string_value: "service-instance-name"
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |       fields {
skywalking_1  |         key: "envoy"
skywalking_1  |         value {
skywalking_1  |           string_value: "isawesome"
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |       fields {
skywalking_1  |         key: "skywalking"
skywalking_1  |         value {
skywalking_1  |           string_value: "iscool"
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |     }
skywalking_1  |     locality {
skywalking_1  |       region: "ap-southeast-1"
skywalking_1  |       zone: "zone1"
skywalking_1  |       sub_zone: "subzone1"
skywalking_1  |     }
skywalking_1  |     user_agent_name: "envoy"
skywalking_1  |     user_agent_build_version {
skywalking_1  |       version {
skywalking_1  |         major_number: 1
skywalking_1  |         minor_number: 19
skywalking_1  |       }
skywalking_1  |       metadata {
skywalking_1  |         fields {
skywalking_1  |           key: "build.type"
skywalking_1  |           value {
skywalking_1  |             string_value: "RELEASE"
skywalking_1  |           }
skywalking_1  |         }
skywalking_1  |         fields {
skywalking_1  |           key: "revision.sha"
skywalking_1  |           value {
skywalking_1  |             string_value: "68fe53a889416fd8570506232052b06f5a531541"
skywalking_1  |           }
skywalking_1  |         }
skywalking_1  |         fields {
skywalking_1  |           key: "revision.status"
skywalking_1  |           value {
skywalking_1  |             string_value: "Clean"
skywalking_1  |           }
skywalking_1  |         }
skywalking_1  |         fields {
skywalking_1  |           key: "ssl.version"
skywalking_1  |           value {
skywalking_1  |             string_value: "BoringSSL"
skywalking_1  |           }
skywalking_1  |         }
skywalking_1  |       }
skywalking_1  |     }
skywalking_1  |     extensions {
skywalking_1  |       name: "composite-action"
skywalking_1  |       category: "envoy.matching.action"
skywalking_1  |     }
                    ......
skywalking_1  |   }
skywalking_1  | }
skywalking_1  | envoy_metrics {
skywalking_1  |   name: "cluster.service_google.update_no_rebuild"
skywalking_1  |   type: COUNTER
skywalking_1  |   metric {
skywalking_1  |     counter {
skywalking_1  |       value: 1.0
skywalking_1  |     }
skywalking_1  |     timestamp_ms: 1627046729718
skywalking_1  |   }
                    ......
skywalking_1  | }
...

$ # To tear down:
$ make down
```

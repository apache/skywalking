# Configuring Envoy to send metrics to SkyWalking

In order to let Envoy to send metrics to SkyWalking, we need to feed Envoy with a configuration which contains `stats_sinks` that includes `envoy.metrics_service`.
This `envoy.metrics_service` should be configured as a [`config.grpc_service`](https://www.envoyproxy.io/docs/envoy/latest/api-v2/api/v2/core/grpc_service.proto#envoy-api-msg-core-grpcservice) entry.

The interesting parts of the config is shown in the config below:

```yaml
stats_sinks:
  - name: envoy.metrics_service
    config:
      grpc_service:
        # Note: we can use google_grpc implementation as well.
        envoy_grpc:
          cluster_name: skywalking_service

static_resources:
  ...
  clusters:
  - name: skywalking_service
    connect_timeout: 5s
    type: LOGICAL_DNS
    http2_protocol_options: {}
    dns_lookup_family: V4_ONLY
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: service_stats
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: skywalking
                # This is the port where SkyWalking serving the Envoy Metrics Service gRPC stream.
                port_value: 11800
```

A more complete static configuration, can be observed here [config.yaml].

Note that Envoy can also be configured dynamically through [xDS Protocol](https://github.com/envoyproxy/data-plane-api/blob/master/XDS_PROTOCOL.md).

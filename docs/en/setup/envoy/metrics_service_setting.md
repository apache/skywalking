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
          cluster_name: service_skywalking

static_resources:
  ...
  clusters:
  - name: service_skywalking
    connect_timeout: 5s
    type: LOGICAL_DNS
    http2_protocol_options: {}
    dns_lookup_family: V4_ONLY
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: service_skywalking
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: skywalking
                # This is the port where SkyWalking serving the Envoy Metrics Service gRPC stream.
                port_value: 11800
```

A more complete static configuration, can be observed [here](config.yaml).

Note that Envoy can also be configured dynamically through [xDS Protocol](https://github.com/envoyproxy/data-plane-api/blob/master/XDS_PROTOCOL.md).

**Attention**: Only use this when Envoy is under Istio's control, because SkyWalking needs to parse the service name and service instance name from the metadata that is injected by Istio. However, if you want to use this without Istio, you need to inject the metadata yourself like this:

```yaml
node:
  # ... other configs
  metadata:
    LABELS:
      app: test-app
    NAME: service-instance-name
```

# Metrics data

Some of the Envoy statistics are listed in this [list](https://www.envoyproxy.io/docs/envoy/latest/configuration/statistics). A sample data that contains identifier can be found [here](identify.json), while the metrics only can be observed [here](metrics.json).

# Send Envoy metrics to SkyWalking with / without Istio

Envoy defines a gRPC service to emit the metrics, whatever implements this protocol can be used to receive the metrics.
SkyWalking has a built-in receiver that implements this protocol so that you can configure Envoy to emit its metrics to SkyWalking.

As an APM system, SkyWalking does not only receive and store the metrics emitted by Envoy, it also analyzes the topology of services and service instances.

**Attention:** There are two versions of Envoy metrics service protocol up to date,
[v2](https://www.envoyproxy.io/docs/envoy/latest/api-v2/api/v2/core/grpc_service.proto#envoy-api-msg-core-grpcservice) and
[v3](https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/metrics/v3/metrics_service.proto), SkyWalking (8.3.0+) supports both of them.

## Configure Envoy to send metrics to SkyWalking without Istio

Envoy can be used with / without Istio's control. This section introduces how to configure the standalone Envoy to send the metrics to SkyWalking.

In order to let Envoy send metrics to SkyWalking, we need to feed Envoy with a configuration which contains `stats_sinks` that includes `envoy.metrics_service`.
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

Note that Envoy can also be configured dynamically through [xDS Protocol](https://github.com/envoyproxy/data-plane-api/blob/main/xds_protocol.rst).

As mentioned above, SkyWalking also builds the topology of services from the metrics, this is because Envoy also carries the service metadata along with the metrics, to feed the Envoy such metadata, another configuration part is as follows:

```yaml
node:
  # ... other configs
  metadata:
    LABELS:
      app: test-app
    NAME: service-instance-name
```

## Configure Envoy to send metrics to SkyWalking with Istio

Typically, Envoy can be also used under Istio's control, where the configurations are much more simple because Istio composes the configurations for you and sends them to Envoy via [xDS Protocol](https://github.com/envoyproxy/data-plane-api/blob/master/xds_protocol.rst).
Istio also automatically injects the metadata such as service name and instance name into the bootstrap configurations.

Under this circumstance, emitting the metrics to SyWalking is as simple as adding the option `--set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800>` to Istio install command, for example:

```shell
istioctl install -y \
  --set profile=demo `# replace the profile as per your need` \
  --set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800> # replace <skywalking.address.port.11800> with your actual SkyWalking OAP address
```

If you already have Istio installed, you can use the following command to apply the config without re-installing Istio:

```shell
istioctl manifest install -y \
  --set profile=demo `# replace the profile as per your need` \
  --set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800> # replace <skywalking.address.port.11800> with your actual SkyWalking OAP address
```

# Metrics data

Some Envoy statistics are listed in this [list](https://www.envoyproxy.io/docs/envoy/v1.17.0/configuration/upstream/cluster_manager/cluster_stats#config-cluster-manager-cluster-stats). A sample data that contains identifier can be found [here](identify.json), while the metrics only can be observed [here](metrics.json).

# Send Envoy metrics to SkyWalking with/without Istio

Envoy defines a gRPC service to emit metrics, and whatever is used to implement this protocol can be used to receive the metrics.
SkyWalking has a built-in receiver that implements this protocol, so you can configure Envoy to emit its metrics to SkyWalking.

As an APM system, SkyWalking not only receives and stores the metrics emitted by Envoy but also analyzes the topology of services and service instances.

**Attention:** There are two versions of the Envoy metrics service protocol currently:
[v2](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v2/api/v2/core/grpc_service.proto#envoy-api-msg-core-grpcservice) and
[v3](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v3/config/metrics/v3/metrics_service.proto). SkyWalking (8.3.0+) supports both of them.

## Configure Envoy to send metrics to SkyWalking without Istio

Envoy can be used with/without Istio. This section explains how you can configure the standalone Envoy to send metrics to SkyWalking.

To let Envoy send metrics to SkyWalking, we need to feed Envoy with a configuration that contains `stats_sinks`, which in turn includes `envoy.metrics_service`.
This `envoy.metrics_service` should be configured as a [`config.grpc_service`](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v2/api/v2/core/grpc_service.proto#envoy-api-msg-core-grpcservice) entry.

The noteworthy parts of the config are shown below:

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

The comprehensive static configuration can be found [here](config.yaml).

Note that Envoy can also be configured dynamically through [xDS Protocol](https://github.com/envoyproxy/envoy/blob/v1.18.2/api/xds_protocol.rst).

As mentioned above, SkyWalking also builds the topology of services from the metrics since Envoy also carries service metadata along with the metrics. To feed Envoy such metadata, see the other part of the configuration as follows:

```yaml
node:
  # ... other configs
  metadata:
    LABELS:
      app: test-app
    NAME: service-instance-name
```

## Configure Envoy to send metrics to SkyWalking with Istio

Typically, Envoy can also be used with Istio. In this case, the configurations are much simpler because Istio composes the configurations for you and sends them to Envoy via [xDS Protocol](https://github.com/envoyproxy/envoy/blob/v1.18.2/api/xds_protocol.rst).
Istio also automatically injects the metadata, such as service name and instance name, into the bootstrap configurations.

Emitting the metrics to SkyWalking is as simple as adding the option `--set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800>` to Istio install command, like this:

```shell
istioctl install -y \
  --set profile=demo # replace the profile as per your need \
  --set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800> \ # replace <skywalking.address.port.11800> with your actual SkyWalking OAP address
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[0]=.*'
```

If you already have Istio installed, you can use the following command to apply the config without re-installing Istio:

```shell
istioctl manifest install -y \
  --set profile=demo # replace the profile as per your need \
  --set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800> \ # replace <skywalking.address.port.11800> with your actual SkyWalking OAP address
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[0]=.*'
```

Note:
`proxyStatsMatcher` is only supported by `Istio 1.8+`.
We recommend using `inclusionRegexps` to reserve specific metrics that need to be analyzed to reduce memory usage and avoid CPU overhead.
For example, OAP uses these metrics:

```shell
istioctl manifest install -y \
  --set profile=demo # replace the profile as per your need \
  --set meshConfig.defaultConfig.envoyMetricsService.address=<skywalking.address.port.11800> \ # replace <skywalking.address.port.11800> with your actual SkyWalking OAP address
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[0]=.*membership_healthy.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[1]=.*upstream_cx_active.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[2]=.*upstream_cx_total.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[3]=.*upstream_rq_active.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[4]=.*upstream_rq_total.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[5]=.*upstream_rq_pending_active.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[6]=.*lb_healthy_panic.*' \
  --set 'meshConfig.defaultConfig.proxyStatsMatcher.inclusionRegexps[7]=.*upstream_cx_none_healthy.*'
```

# Metrics data

Some Envoy statistics are [listed here](https://www.envoyproxy.io/docs/envoy/v1.17.0/configuration/upstream/cluster_manager/cluster_stats#config-cluster-manager-cluster-stats). Sample data that contain identifiers can be found [here](identify.json), while the metrics can be found [here](metrics.json).

# Network Monitoring

SkyWalking supports network monitoring of the data plane in the Service Mesh. [Read this documentation](../backend/backend-k8s-network-monitoring.md) for learn more.
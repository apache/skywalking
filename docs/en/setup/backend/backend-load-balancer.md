# Backend Load Balancer

When setting the Agent or Envoy to connect to the OAP server directly by default,
the OAP server cluster would face the problem of load imbalance. This issue
becomes severe in high-traffic load scenarios. In this doc, we will introduce
two means to solve the problem.

## SkyWalking Satellite Project

[SkyWalking Satellite](https://github.com/apache/skywalking-satellite) is recommended to be
used as a native gateway proxy to provide load balancing capabilities for data content before the data from Agent/ Envoy
reaches the OAP. The major difference between Satellite and other general wide used proxy(s), like Envoy, is that it would route the data accordingly to contents rather than connection, as gRPC streaming is used widely in SkyWalking.

Follow instructions in the [Setup SkyWalking Satellite](https://skywalking.apache.org/docs/#SkyWalkingSatellite)
to deploy Satellite and connect your application to the Satellite.

[Scaling with Apache SkyWalking](https://skywalking.apache.org/blog/2022-01-24-scaling-with-apache-skywalking/) blog
introduces the theory and technology details on how to set up a load balancer for the OAP cluster.

## Envoy Filter to Limit Connections Per OAP Instance

If you don't want to deploy skywalking-satellite, you can enable Istio sidecar
injection for SkyWalking OAP Pods,

```shell
kubectl label namespace $SKYWALKING_NAMESPACE istio-injection=enabled
kubectl -n $SKYWALKING_NAMESPACE rollout restart -l app=skywalking,component=oap
```

and apply an EnvoyFilter to limit the connections per OAP instance, so that
each of the OAP instance can have similar amount of gRPC connections.

Before that, you need to calculate the number of connections for each OAP
instance as follows:

```
NUMBER_OF_SERVICE_PODS=<the-number-of-service-pods-that-are-monitored-by-skywalking>

# Each service Pod has 2 connections to OAP
NUMBER_OF_TOTAL_CONNECTIONS=$((NUMBER_OF_SERVICE_PODS * 2))

# Divide the total connections by the replicas of OAP
NUMBER_OF_CONNECTIONS_PER_OAP=$((NUMBER_OF_TOTAL_CONNECTIONS / $NUMBER_OF_OAP_REPLICAS))
```

And you can apply an EnvoyFilter to limit connections:

```shell
kubectl -n $SKYWALKING_NAMESPACE apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: oap-limit-connections
  namespace: istio-system
spec:
  configPatches:
  - applyTo: NETWORK_FILTER
    match:
      context: ANY
      listener:
        filterChain:
          filter:
            name: envoy.filters.network.http_connection_manager
        portNumber: 11800
    patch:
      operation: INSERT_BEFORE
      value:
        name: envoy.filters.network.ConnectionLimit
        typed_config:
          '@type': type.googleapis.com/envoy.extensions.filters.network.connection_limit.v3.ConnectionLimit
          max_connections: $NUMBER_OF_CONNECTIONS_PER_OAP
          stat_prefix: envoy_filters_network_connection_limit
  workloadSelector:
    labels:
      app: oap
EOF
```

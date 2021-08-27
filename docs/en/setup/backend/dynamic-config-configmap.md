# Dynamic Configuration Kuberbetes Configmap Implementation

[configmap](https://kubernetes.io/docs/concepts/configuration/configmap/) is also supported as Dynamic Configuration Center (DCC). To use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:k8s-configmap}
  # [example] (../../../../oap-server/server-configuration/configuration-k8s-configmap/src/test/resources/skywalking-dynamic-configmap.example.yaml)
  k8s-configmap:
    # Sync period in seconds. Defaults to 60 seconds.
    period: ${SW_CONFIG_CONFIGMAP_PERIOD:60}
    # Which namespace is confiigmap deployed in.
    namespace: ${SW_CLUSTER_K8S_NAMESPACE:default}
    # Labelselector is used to locate specific configmap
    labelSelector: ${SW_CLUSTER_K8S_LABEL:app=collector,release=skywalking}
```
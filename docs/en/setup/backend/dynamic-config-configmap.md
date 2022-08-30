# Dynamic Configuration Kubernetes Configmap Implementation

[configmap](https://kubernetes.io/docs/concepts/configuration/configmap/) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:k8s-configmap}
  # [example] (../../../../oap-server/server-configuration/configuration-k8s-configmap/src/test/resources/skywalking-dynamic-configmap.example.yaml)
  k8s-configmap:
    # Sync period in seconds. Defaults to 60 seconds.
    period: ${SW_CONFIG_CONFIGMAP_PERIOD:60}
    # Which namespace is configmap deployed in.
    namespace: ${SW_CLUSTER_K8S_NAMESPACE:default}
    # Labelselector is used to locate specific configmap
    labelSelector: ${SW_CLUSTER_K8S_LABEL:app=collector,release=skywalking}
```
`{namespace}` is the k8s namespace to which the configmap belongs.
`{labelSelector}` is used to identify which configmaps would be selected.

e.g. These 2 configmaps would be selected by the above config:
```
apiversion: v1
kind: ConfigMap
metadata:
  name: skywalking-dynamic-config
  namespace: default
  labels:
    app: collector
    release: skywalking
data:
  configKey1: configValue1
  configKey2: configValue2
  ...
```
```
apiversion: v1
kind: ConfigMap
metadata:
  name: skywalking-dynamic-config2
  namespace: default
  labels:
    app: collector
    release: skywalking
data:
  configKey3: configValue3
  ...
```

## Config Storage
The configs are configmap data items, as the above example shows. we can organize the configs in 1 or more configmap files.
### Single Config
Under configmap.data:
```
  configKey: configValue
```
e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
The config in configmap is:
```
apiversion: v1
kind: ConfigMap
metadata:
  name: skywalking-dynamic-config
  namespace: default
  labels:
    app: collector
    release: skywalking
data:
  agent-analyzer.default.slowDBAccessThreshold: default:200,mongodb:50
```
### Group Config
The `data key` is composited by configKey and subItemKey to identify it is a group config:
```
configKey.subItemKey1: subItemValue1
configKey.subItemKey2: subItemValue2
...
```
e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
The config can separate into 2 configmaps is:
```
apiversion: v1
kind: ConfigMap
metadata:
  name: skywalking-dynamic-config
  namespace: default
  labels:
    app: collector
    release: skywalking
data:
  core.default.endpoint-name-grouping-openapi.customerAPI-v1: value of customerAPI-v1
  core.default.endpoint-name-grouping-openapi.productAPI-v1: value of productAPI-v1
```
```
apiversion: v1
kind: ConfigMap
metadata:
  name: skywalking-dynamic-config2
  namespace: default
  labels:
    app: collector
    release: skywalking
data:
  core.default.endpoint-name-grouping-openapi.productAPI-v2: value of productAPI-v2
```
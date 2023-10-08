# Observe Service Mesh through ALS

[Envoy Access Log Service (ALS)](https://www.envoyproxy.io/docs/envoy/v1.18.2/api-v2/service/accesslog/v2/als.proto) provides
full logs on routed RPC, including HTTP and TCP.

## Background

The solution was initialized and first implemented by [Sheng Wu](https://github.com/wu-sheng), [Hongtao Gao](https://github.com/hanahmily), [Lizan Zhou](https://github.com/lizan),
and [Dhi Aurrahman](https://github.com/dio) on May 17, 2019, and was presented at [KubeCon China 2019](https://kccncosschn19eng.sched.com/event/NroB/observability-in-service-mesh-powered-by-envoy-and-apache-skywalking-sheng-wu-lizan-zhou-tetrate).
Here is a [video recording of the presentation](https://www.youtube.com/watch?v=tERm39ju9ew).

SkyWalking is the first open-source project that introduced an ALS-based solution to the world. This solution provides a new take on observability with a lightweight payload on the service mesh.

## Enable ALS and SkyWalking Receiver

You need the following steps to set up ALS.

- Enable [`envoyAccessLogService` in ProxyConfig](https://istio.io/docs/reference/config/istio.mesh.v1alpha1/#ProxyConfig) and set the ALS address to where the SkyWalking OAP listens.
In Istio version 1.6.0+, if Istio is installed with [`demo` profile](https://istio.io/latest/docs/setup/additional-setup/config-profiles/), you can enable ALS with this command:

   ```shell
   istioctl manifest apply \
     --set profile=demo \
     --set meshConfig.enableEnvoyAccessLogService=true \
     --set meshConfig.defaultConfig.envoyAccessLogService.address=<skywalking-oap.skywalking.svc:11800>
   ```

   Note: Replace `<skywalking-oap.skywalking.svc:11800>` with the real address where SkyWalking OAP is deployed.

- Activate SkyWalking Envoy Receiver. (activated in default)

```yaml
envoy-metric:
  selector: ${SW_ENVOY_METRIC:default}
```

- Choose an ALS analyzer. There are two available analyzers for both HTTP access logs and TCP access logs: `k8s-mesh` and `mx-mesh`.
  Set the system environment variables **SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS** and **SW_ENVOY_METRIC_ALS_TCP_ANALYSIS**,
  such as `SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS=mx-mesh` and `SW_ENVOY_METRIC_ALS_TCP_ANALYSIS=mx-mesh`, or in `application.yaml` to activate the analyzers. For more about the analyzers, see [SkyWalking ALS Analyzers](#skywalking-als-analyzers).

   ```yaml
   envoy-metric:
     selector: ${SW_ENVOY_METRIC:default}
     default:
       acceptMetricsService: ${SW_ENVOY_METRIC_SERVICE:true}
       alsHTTPAnalysis: ${SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS:""} # Setting the system env variable would override this.
       alsTCPAnalysis: ${SW_ENVOY_METRIC_ALS_TCP_ANALYSIS:""}
   ```

   To use multiple analyzers as a fallback, please use `,` to concatenate.

## Example

Here's an example of installing Istio and deploying SkyWalking by Helm chart.

```shell
istioctl install \
  --set profile=demo \
  --set meshConfig.enableEnvoyAccessLogService=true \
  --set meshConfig.defaultConfig.envoyAccessLogService.address=skywalking-oap.istio-system:11800

git clone https://github.com/apache/skywalking-helm.git
cd skywalking-helm/chart

helm repo add elastic https://helm.elastic.co

helm dep up skywalking

helm install 8.1.0 skywalking -n istio-system \
  --set oap.env.SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS=mx-mesh \
  --set oap.env.SW_ENVOY_METRIC_ALS_TCP_ANALYSIS=mx-mesh \
  --set fullnameOverride=skywalking \
  --set oap.envoy.als.enabled=true
```

You can use `kubectl -n istio-system logs -l app=skywalking | grep "K8sALSServiceMeshHTTPAnalysis"` to ensure that OAP ALS `mx-mesh` analyzer has been activated.

## SkyWalking ALS Analyzers

There are several available analyzers: `k8s-mesh`, `mx-mesh`, and `persistence`. You can specify one or more
analyzers to analyze the access logs. When multiple analyzers are specified, it acts as a fast-success mechanism:
SkyWalking loops over the analyzers and use them to analyze the logs. Once there is an analyzer that is able to produce a
result, it stops the loop.

### `k8s-mesh`

`k8s-mesh` uses the metadata from Kubernetes clusters, hence in this analyzer, OAP needs access roles to `Pod`, `Service`, and `Endpoints`.

The [blog](https://skywalking.apache.org/blog/2020-12-03-obs-service-mesh-with-sw-and-als/) illustrates the details of how it works and a step-by-step tutorial to apply it to the `bookinfo` application.

### `mx-mesh`

`mx-mesh` uses the Envoy metadata exchange mechanism to get the service name, etc.
This analyzer requires Istio to enable the metadata exchange plugin (you can enable it by `--set values.telemetry.v2.enabled=true`,
or if you're using Istio 1.7+ and installing it with profile `demo`/`preview`, it should already be enabled).

The [blog](https://skywalking.apache.org/blog/obs-service-mesh-vm-with-sw-and-als/) illustrates the details of how it works and a step-by-step tutorial on applying it to the [Online Boutique](https://github.com/GoogleCloudPlatform/microservices-demo) system.

### `persistence`

`persistence` analyzer adapts the Envoy access log format to
SkyWalking's [native log format](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto), and forwards the formatted logs to [LAL](../../concepts-and-designs/lal.md), where you can configure persistent
conditions, such as `sampler`, only persist error logs, etc. SkyWalking provides a default configuration
file [`envoy-als.yaml`](../../../../oap-server/server-starter/src/main/resources/lal/envoy-als.yaml) that you can
adjust as per your needs. Please make sure to activate this rule via adding the rule name `envoy-als`
into config item `log-analyzer/default/lalFiles` (or environment variable `SW_LOG_LAL_FILES`,
e.g. `SW_LOG_LAL_FILES=envoy-als`).

**Attention**: Since the `persistence` analyzer also needs a mechanism to map the logs into responding services, you need to configure at least one of `k8s-mesh` or `mx-mesh` as its antecedent so that `persistence` analyzer knows which service the logs belong to. For example, you should set `envoy-metric/default/alsHTTPAnalysis` (or environment
variable `SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS`) to something like `k8s-mesh,persistence`, `mx-mesh,persistence`, or `mx-mesh,k8s-mesh,persistence`.

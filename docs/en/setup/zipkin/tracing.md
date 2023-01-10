# Observe Service Mesh through Zipkin traces

Istio has built-in support to generate Zipkin traces from Envoy proxy sidecar,
and SkyWalking can serve as a Zipkin server to collect and provide query APIs for these traces,
you can deploy SkyWalking to replace Zipkin server in Istio, and point the Zipkin address to
SkyWalking. SkyWalking also embeds Zipkin Lens UI as part of SkyWalking UI,
you can use it to query Zipkin traces.

## Enable Zipkin Traces Receiver

SkyWalking has built-in Zipkin receiver, you can enable it by setting `receiver-zipkin` to `default`
in `application.yml`, or by setting environment variable `SW_RECEIVER_ZIPKIN=default` before
starting OAP server:

```yaml
receiver-zipkin:
  selector: ${SW_RECEIVER_ZIPKIN:default}
  default:
    # Other configurations...
```

After enabling the Zipkin receiver, SkyWalking listens on port 9411 for Zipkin traces, you can just
change the Zipkin server address to SkyWalking's address with 9411 as the port.

## Enable Zipkin Traces Query Module

If you want to query Zipkin traces from SkyWalking, you need to enable the Zipkin traces query module
by setting `query-zipkin` to `default` in `application.yml`, or by setting environment variable
`SW_QUERY_ZIPKIN=default` before starting OAP server:

```yaml
query-zipkin:
  selector: ${SW_QUERY_ZIPKIN:default}
  default:
    # Other configurations
```

After enabling Zipkin query module, SkyWalking listens on port 9412 for Zipkin query APIs, you can
also query the Zipkin traces from SkyWalking UI, menu `Service Mesh --> Services --> Zipkin Trace`.

## Set Up Zipkin Traces in Istio

When installing Istio, you can enable Zipkin tracing and point it to SkyWalking by setting

```shell
istioctl install -y --set profile=demo \
		--set meshConfig.defaultConfig.tracing.sampling=100 \
		--set meshConfig.defaultConfig.tracing.zipkin.address=oap.istio-system.svc.cluster.local:9411 \
		--set meshConfig.enableTracing=true
```

so that Istio proxy (Envoy) can generate traces and sent them to SkyWalking.

For more details about Zipkin on Istio, refer to [the Istio doc](https://istio.io/latest/docs/tasks/observability/distributed-tracing/zipkin/).

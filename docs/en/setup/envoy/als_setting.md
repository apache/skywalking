# Observe service mesh through ALS
Envoy [ALS(access log service)](https://www.envoyproxy.io/docs/envoy/latest/api-v2/service/accesslog/v2/als.proto) provides
fully logs about RPC routed, including HTTP and TCP.

You need three steps to open ALS.
1. Open envoyAccessLogService at istio. See [more](https://github.com/istio/istio/blob/22198bd29b224139b9614fe506e5c07716928031/install/kubernetes/helm/istio/values.yaml#L179)
on istio helm chart.
```yaml
    # Configure envoy gRPC access log service.
    envoyAccessLogService:
      enabled: false
      host: # example: accesslog-service.istio-system
      port: # example: 15000
      tlsSettings:
        mode: DISABLE # DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
        clientCertificate: # example: /etc/istio/als/cert-chain.pem
        privateKey: # example: /etc/istio/als/key.pem
        caCertificates: # example: /etc/istio/als/root-cert.pem
        sni: # example: als.somedomain
        subjectAltNames: []
        # - als.somedomain 
      tcpKeepalive:
        probes: 3
        time: 10s
        interval: 10s
```
2. Open SkyWalking [envoy receiver](../backend/backend-receivers.md).
3. Active ALS k8s-mesh analysis
```yaml
envoy-metric:
  default:
    alsHTTPAnalysis:
      - k8s-mesh
```

Notice, only use this when using envoy under Istio controlled.
Otherwise, you need to implement your own `ALSHTTPAnalysis` and register it to receiver.
# Open Fetcher
Fetcher is a concept in SkyWalking backend. It uses pulling mode rather than [receiver](backend-receivers.md), which
read the data from the target systems. This mode is typically in some metrics SDKs, such as Prometheus.

## Prometheus Fetcher
```yaml
prometheus-fetcher:
  selector: ${SW_PROMETHEUS_FETCHER:default}
  default:
    active: ${SW_PROMETHEUS_FETCHER_ACTIVE:false}
``` 

TODO: More detail should be added when this fetcher provided.
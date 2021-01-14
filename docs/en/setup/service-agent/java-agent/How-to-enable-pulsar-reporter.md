# How to enable Pulsar Reporter

The Pulsar reporter plugin support report traces, JVM metrics, Instance Properties, and profiled snapshots to pulsar cluster like kafka reporter, which is disabled in default. Move the jar of the plugin, `pulsar-reporter-plugin-x.y.z.jar`, from `agent/optional-reporter-plugins` to `agent/plugins` for activating.

Notice, currently, the agent still needs to configure GRPC receiver for delivering the task of profiling. In other words, the following configure cannot be omitted.

```properties
# Backend service addresses.
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:127.0.0.1:11800}

# Pulsar producer configuration
plugin.pulsar.service_url=${SW_PULSAR_SERVICE_URL:pulsar://loaclhost:6650}
```

Pulsar reporter plugin support to customize all configurations of listed in [here](http://pulsar.apache.org/docs/en/client-libraries-java/#producer).

Before you activated the pulsar reporter, you have to make sure that OAPServer [pulsar fetcher](../../backend/backend-fetcher.md#pulsar-fetcher) has been opened in service.

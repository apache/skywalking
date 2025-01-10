# Circuit Breaking

Circuit breaking is a mechanism used to detect failures and encapsulate the logic of preventing OAP node crashing. It is
a key component of SkyWalking's resilience strategy. This approach protects the system from overload and ensures
stability.

Currently, there are two available strategies for circuit breaking: heap memory usage and direct memory pool size.

```yaml
# The int value of the max heap memory usage percent. The default value is 85%.
maxHeapMemoryUsagePercent: ${SW_CORE_MAX_HEAP_MEMORY_USAGE_PERCENT:85}
# The long value of the max direct memory usage. The default max value is -1, representing no limit. The unit is in bytes.
maxDirectMemoryUsage: ${SW_CORE_MAX_DIRECT_MEMORY_USAGE:-1}
```

SkyWalking's circuit breaker mechanism actively rejects new telemetry data from gRPC.
We keep HTTP services running to serve the UI and API, and Prometheus telemetry data is still available.

Note, this feature relies on the `prometheus` provider in [Telemetry for backend](backend-telemetry.md) to monitor OAP
server status.
# Message Queue performance and consuming latency monitoring

Message Queue server plays an important role in today's distributed system, in order to reduce the length and latency of
blocking RPC, and eventually improve user experience. But in this async way, the measure for queue consuming traffic and
latency becomes significant.

Since 8.9.0, SkyWalking leverages native tracing agent and [**Extension Header
Item** of SkyWalking Cross Process Propagation Headers Protocol v3](../../protocols/skywalking-cross-process-propagation-headers-protocol-v3.md#extension-header-item)
, to provide performance monitoring for Message Queue system.

In default, we provide `Message Queue Consuming Count` and `Message Queue Avg Consuming Latency` metrics for service and
endpoint levels.

More metrics could be added through `core.oal`.

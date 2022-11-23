# Virtual Cache

Virtual cache represent the cache nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the cache are also from the Cache client-side perspective.

For example, Redis plugins in the Java agent could detect the latency of command
As a result, SkyWalking would show traffic, latency, success rate, and sampled slow operations(write/read) powered by backend analysis capabilities in this dashboard.

The cache operation span should have
- It is an **Exit** or **Local** span
- **Span's layer == CACHE**
- Tag key = `cache.type`, value = The type of cache system , e.g. redis
- Tag key = `cache.op`, value = `read` or `write` , indicates the value of tag `cache.cmd` is used for `write` or `read` operation
- Tag key = `cache.cmd`, value = the cache command , e.g. get,set,del
- Tag key = `cache.key`, value = the cache key
- If the cache system is in-memory (e.g. Guava-cache), agents' plugin would create a local span usually, and the span's peer would be null ,otherwise the peer is the network address(IP or domain) of Cache server.

Ref [slow cache doc](../backend/slow-cache-command.md) to know more slow Cache commands settings. 
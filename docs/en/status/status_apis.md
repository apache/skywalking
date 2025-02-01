# Status APIs

Status APIs are a set of APIs that allow you to get the status of the OAP status and measurements of queries.
They are useful for monitoring the health of the OAP server and to diagnose and troubleshoot issues caused by
configurations and performance bottlenecks.

Since v10, we begin to add status APIs to help users to understand the status of the OAP server, besides looking the raw
logs and self-observability solutions.

- [Dump Effective Initial Configurations API](../debugging/config_dump.md)
- [Tracing Query Execution APIs](../debugging/query-tracing.md)
- [Get Effective TTL Configurations API](query_ttl_setup.md)
- [Query Cluster Nodes API](query_cluster_nodes.md)

If you have a proposal about new status API, please don't hesitate
to [create a discussion](https://github.com/apache/skywalking/discussions/new?category=ideas).
The basic principles for a status API are

1. It should be useful for users to understand the status of the OAP server or the reason of the performance behavior,
   rather than a function feature.
2. It should be on-demand and not impact the performance of the OAP server too much, especially in the production
   environment.
3. HTTP APIs are preferred unless there is a special reason to use other.

## Disable Status APIs

By default, this service is open for helping users to debug and diagnose. If you want to disable it, you need to disable
the whole `status-query` module through setting `selector=-`.

```yaml
status-query:
  selector: ${SW_STATUS_QUERY:default}
```
